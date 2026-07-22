package com.seedrank.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
@Import({TestcontainersConfiguration.class, AiJobFinalFailureIntegrationTest.FinalFailureConfiguration.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.ai.prompt-version=idea-candidates-v1",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class AiJobFinalFailureIntegrationTest {

    private static final Instant START = Instant.parse("2026-07-22T02:30:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiCandidateProcessingService processor;
    @Autowired AdjustableClock clock;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        clock.set(START);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void endsAfterThreeTransientFailuresAndReturnsManualDraftFormToOwner() throws Exception {
        String token = signupAndLogin("final-failure@example.com", "final_failure");
        UUID jobId = createJob(token);

        assertThat(processor.processNext("failure-worker")).isEqualTo(AiCandidateProcessingOutcome.RETRY_SCHEDULED);
        clock.advanceSeconds(30);
        assertThat(processor.processNext("failure-worker")).isEqualTo(AiCandidateProcessingOutcome.RETRY_SCHEDULED);
        clock.advanceSeconds(60);
        assertThat(processor.processNext("failure-worker")).isEqualTo(AiCandidateProcessingOutcome.FAILED_FINAL);

        assertThat(jdbc.queryForMap(
                "SELECT status, retry_count, next_attempt_at, lease_owner, lease_token, locked_until, failure_code "
                        + "FROM ai_jobs WHERE id=?", jobId))
                .containsEntry("status", "FAILED")
                .containsEntry("retry_count", 3)
                .containsEntry("failure_code", "AI_GENERATION_FAILED")
                .containsEntry("next_attempt_at", null)
                .containsEntry("lease_owner", null)
                .containsEntry("lease_token", null)
                .containsEntry("locked_until", null);

        var response = mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("AI_GENERATION_FAILED"))
                .andExpect(jsonPath("$.result").isEmpty())
                .andExpect(jsonPath("$.manualForm.title").value(""))
                .andExpect(jsonPath("$.manualForm.category").value(""))
                .andExpect(jsonPath("$.manualForm.summary").value(""))
                .andExpect(jsonPath("$.manualForm.problem").value(""))
                .andExpect(jsonPath("$.manualForm.targetCustomer").value(""))
                .andExpect(jsonPath("$.manualForm.solution").value(""))
                .andExpect(jsonPath("$.manualForm.businessModel").value(""))
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain(
                "TIMEOUT", "RATE_LIMITED", "SERVER_ERROR", "retryCount", "leaseToken", "inputSnapshot");
    }

    @Test
    void doesNotReturnManualFormBeforeFinalFailure() throws Exception {
        String token = signupAndLogin("pending@example.com", "pending_form");
        UUID jobId = createJob(token);

        mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.failureCode").isEmpty())
                .andExpect(jsonPath("$.manualForm").isEmpty());
    }

    @Test
    void publishesTheManualFormInTheOwnerQueryOpenApiSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.AiJobResultResponse.properties.manualForm").exists())
                .andExpect(jsonPath("$.components.schemas.AiJobManualForm.properties.title").exists())
                .andExpect(jsonPath("$.components.schemas.AiJobManualForm.properties.businessModel").exists());
    }

    private UUID createJob(String token) throws Exception {
        var response = mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "final-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"AI 장애\",\"background\":\"수동 작성 fallback 검증\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).get("jobId").asText());
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("accessToken").asText();
    }

    private void cleanDatabase() {
        jdbc.update("DELETE FROM ai_generation_results");
        jdbc.update("DELETE FROM ai_jobs");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @TestConfiguration
    static class FinalFailureConfiguration {
        @Bean
        @Primary
        AiCandidateProvider alwaysFailingProvider() {
            return (input, prompt) -> { throw new AiProviderException(AiJobFailure.SERVER_ERROR); };
        }

        @Bean
        @Primary
        AdjustableClock adjustableClock() {
            return new AdjustableClock(START);
        }
    }

    static class AdjustableClock extends Clock {
        private volatile Instant current;

        AdjustableClock(Instant current) { this.current = current; }
        void set(Instant instant) { current = instant; }
        void advanceSeconds(long seconds) { current = current.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
