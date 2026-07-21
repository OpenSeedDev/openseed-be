package com.seedrank.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, AiJobWorkerIntegrationTest.WorkerClockConfiguration.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.ai.prompt-version=idea-candidates-v1"
})
@AutoConfigureMockMvc
class AiJobWorkerIntegrationTest {

    private static final Instant START = Instant.parse("2026-07-21T12:30:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiJobWorkerService worker;
    @Autowired AdjustableClock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        clock.set(START);
        jdbc.update("DELETE FROM ai_jobs");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void claimsTheOldestPendingJobWithATwoMinuteLease() throws Exception {
        String token = signupAndLogin("claim@example.com", "claim_worker");
        UUID first = createJob(token, "first");
        clock.advanceSeconds(1);
        createJob(token, "second");

        AiJobClaim claim = worker.claimNext("worker-a").orElseThrow();

        assertThat(claim.jobId()).isEqualTo(first);
        assertThat(claim.retryCount()).isZero();
        assertThat(claim.lockedUntil()).isEqualTo(clock.instant().plusSeconds(120));
        assertThat(claim.inputSnapshot()).contains("keyword", "background");
        assertThat(claim.promptVersion()).isEqualTo("idea-candidates-v1");
        assertThat(jdbc.queryForMap("SELECT status, lease_owner, lease_token, locked_until FROM ai_jobs WHERE id=?", first))
                .containsEntry("status", "PROCESSING")
                .containsEntry("lease_owner", "worker-a")
                .containsEntry("lease_token", claim.leaseToken());
    }

    @Test
    void concurrentWorkersNeverClaimTheSameJobTwice() throws Exception {
        String token = signupAndLogin("concurrent@example.com", "concurrent_worker");
        UUID jobId = createJob(token, "concurrent");
        int workers = 6;
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Optional<AiJobClaim>>();

        try (var executor = Executors.newFixedThreadPool(workers)) {
            var futures = new ArrayList<java.util.concurrent.Future<Optional<AiJobClaim>>>();
            for (int index = 0; index < workers; index++) {
                int workerIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return worker.claimNext("worker-" + workerIndex);
                }));
            }
            ready.await();
            start.countDown();
            for (var future : futures) results.add(future.get());
        }

        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        assertThat(results.stream().flatMap(Optional::stream).map(AiJobClaim::jobId)).containsExactly(jobId);
    }

    @Test
    void recoversAnExpiredLeaseWithANewFencingTokenAndRejectsTheOldToken() throws Exception {
        String token = signupAndLogin("recover@example.com", "recover_worker");
        UUID jobId = createJob(token, "recover");
        AiJobClaim first = worker.claimNext("worker-old").orElseThrow();
        assertThat(worker.claimNext("worker-active")).isEmpty();

        clock.advanceSeconds(121);
        assertThatThrownBy(() -> worker.scheduleRetry(jobId, first.leaseToken(), AiJobFailure.TIMEOUT))
                .isInstanceOf(StaleAiJobLeaseException.class);
        AiJobClaim recovered = worker.claimNext("worker-new").orElseThrow();

        assertThat(recovered.jobId()).isEqualTo(jobId);
        assertThat(recovered.leaseToken()).isNotEqualTo(first.leaseToken());
        assertThatThrownBy(() -> worker.scheduleRetry(jobId, first.leaseToken(), AiJobFailure.TIMEOUT))
                .isInstanceOf(StaleAiJobLeaseException.class);
        assertThat(jdbc.queryForObject("SELECT lease_owner FROM ai_jobs WHERE id=?", String.class, jobId))
                .isEqualTo("worker-new");
    }

    @ParameterizedTest
    @EnumSource(AiJobFailure.class)
    void retriesTimeoutRateLimitAndServerErrorsAfterBackoff(AiJobFailure failure) throws Exception {
        String token = signupAndLogin(failure.name().toLowerCase() + "@example.com", "retry_" + failure.name().toLowerCase());
        UUID jobId = createJob(token, "retry-" + failure.name());
        AiJobClaim claim = worker.claimNext("retry-worker").orElseThrow();

        worker.scheduleRetry(jobId, claim.leaseToken(), failure);

        var row = jdbc.queryForMap(
                "SELECT status, retry_count, next_attempt_at, lease_owner, lease_token, locked_until FROM ai_jobs WHERE id=?",
                jobId);
        assertThat(row).containsEntry("status", "RETRY_WAIT").containsEntry("retry_count", 1);
        assertThat(row.get("next_attempt_at")).isEqualTo(java.sql.Timestamp.from(START.plusSeconds(30)));
        assertThat(row.get("lease_owner")).isNull();
        assertThat(row.get("lease_token")).isNull();
        assertThat(row.get("locked_until")).isNull();

        clock.advanceSeconds(29);
        assertThat(worker.claimNext("too-early")).isEmpty();
        clock.advanceSeconds(1);
        assertThat(worker.claimNext("on-time")).get().extracting(AiJobClaim::jobId).isEqualTo(jobId);
    }

    @Test
    void increasesBackoffExponentiallyAndCapsItAtFifteenMinutes() throws Exception {
        String token = signupAndLogin("backoff@example.com", "backoff_worker");
        UUID jobId = createJob(token, "backoff");
        long[] expectedDelays = {30, 60, 120, 240, 480, 900, 900};

        for (long expectedDelay : expectedDelays) {
            AiJobClaim claim = worker.claimNext("worker-backoff").orElseThrow();
            Instant failedAt = clock.instant();
            worker.scheduleRetry(jobId, claim.leaseToken(), AiJobFailure.SERVER_ERROR);
            assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM ai_jobs WHERE id=?", Instant.class, jobId))
                    .isEqualTo(failedAt.plusSeconds(expectedDelay));
            clock.advanceSeconds(expectedDelay);
        }

        assertThat(jdbc.queryForObject("SELECT retry_count FROM ai_jobs WHERE id=?", Integer.class, jobId)).isEqualTo(7);
    }

    private UUID createJob(String token, String key) throws Exception {
        var response = mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"AI Worker\",\"background\":\"Lease와 Backoff 검증\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        return UUID.fromString(objectMapper.readTree(response.getContentAsString()).get("jobId").asText());
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString()).get("accessToken").asText();
    }

    @TestConfiguration
    static class WorkerClockConfiguration {
        @Bean
        @Primary
        AdjustableClock adjustableClock() {
            return new AdjustableClock(START);
        }
    }

    static class AdjustableClock extends Clock {
        private volatile Instant current;

        AdjustableClock(Instant current) {
            this.current = current;
        }

        void set(Instant instant) { current = instant; }
        void advanceSeconds(long seconds) { current = current.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
