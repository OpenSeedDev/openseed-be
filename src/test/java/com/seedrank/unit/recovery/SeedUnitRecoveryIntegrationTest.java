package com.seedrank.unit.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, SeedUnitRecoveryIntegrationTest.FixedClockConfig.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class SeedUnitRecoveryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-22T06:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM seed_unit_recoveries");
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void recoversWholeLotAtExactUnlockUsingCurrentPriceAtomically() throws Exception {
        Session owner = signupAndLogin("recover-owner@example.com", "recover_owner");
        Session author = signupAndLogin("recover-author@example.com", "recover_author");
        UUID ideaId = insertIdea(author.userId(), 15);
        UUID lotId = insertLot(ideaId, owner.userId(), 10, NOW.minusSeconds(86_400));

        recover(owner.token(), lotId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotId").value(lotId.toString()))
                .andExpect(jsonPath("$.units").value(10))
                .andExpect(jsonPath("$.recoveryPrice").value(15))
                .andExpect(jsonPath("$.realizedAmount").value(150))
                .andExpect(jsonPath("$.walletPaidAmount").value(150))
                .andExpect(jsonPath("$.pendingAmount").value(0))
                .andExpect(jsonPath("$.balanceAfter").value(480))
                .andExpect(jsonPath("$.nonMonetaryNotice").exists());

        assertThat(jdbc.queryForObject("SELECT status FROM seed_unit_lots WHERE id=?", String.class, lotId))
                .isEqualTo("RECOVERED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seed_unit_recoveries WHERE lot_id=?", Integer.class, lotId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM point_ledgers WHERE source_type='UNIT_RECOVERY' AND source_id=?",
                Integer.class, lotId)).isEqualTo(1);
    }

    @Test
    void rejectsBeforeUnlockAndAllowsTheExactBoundary() throws Exception {
        Session owner = signupAndLogin("boundary-owner@example.com", "boundary_owner");
        Session author = signupAndLogin("boundary-author@example.com", "boundary_author");
        UUID ideaId = insertIdea(author.userId(), 10);
        UUID locked = insertLot(ideaId, owner.userId(), 1, NOW.minusSeconds(86_399));
        UUID unlocked = insertLot(ideaId, owner.userId(), 1, NOW.minusSeconds(86_400));

        recover(owner.token(), locked, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNIT_LOCKED"));
        recover(owner.token(), unlocked, null).andExpect(status().isOk());
    }

    @Test
    void hidesAnotherUsersLotAndRejectsPartialRecoveryWithoutSideEffects() throws Exception {
        Session owner = signupAndLogin("private-owner@example.com", "private_owner");
        Session other = signupAndLogin("private-other@example.com", "private_other");
        UUID ideaId = insertIdea(other.userId(), 10);
        UUID lotId = insertLot(ideaId, owner.userId(), 5, NOW.minusSeconds(86_400));

        recover(other.token(), lotId, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNIT_LOT_NOT_FOUND"));
        recover(owner.token(), lotId, "{\"units\":2}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARTIAL_RECOVERY_NOT_SUPPORTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM seed_unit_lots WHERE id=?", String.class, lotId))
                .isEqualTo("LOCKED");
    }

    @Test
    void paysOnlyWalletCapacityAndFreezesTheRemainderAsPending() throws Exception {
        Session owner = signupAndLogin("cap-owner@example.com", "cap_owner");
        Session author = signupAndLogin("cap-author@example.com", "cap_author");
        jdbc.update("UPDATE point_wallets SET balance=1950 WHERE user_id=?", owner.userId());
        UUID ideaId = insertIdea(author.userId(), 100);
        UUID lotId = insertLot(ideaId, owner.userId(), 5, NOW.minusSeconds(86_400));

        recover(owner.token(), lotId, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedAmount").value(500))
                .andExpect(jsonPath("$.walletPaidAmount").value(50))
                .andExpect(jsonPath("$.pendingAmount").value(450))
                .andExpect(jsonPath("$.balanceAfter").value(2000))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(450));

        jdbc.update("UPDATE ideas SET current_unit_price=1 WHERE id=?", ideaId);
        recover(owner.token(), lotId, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryPrice").value(100))
                .andExpect(jsonPath("$.pendingAmount").value(450));
    }

    @Test
    void appliesTheFiveHundredPointDailyPaymentLimitAcrossLots() throws Exception {
        Session owner = signupAndLogin("daily-owner@example.com", "daily_owner");
        Session author = signupAndLogin("daily-author@example.com", "daily_author");
        UUID ideaId = insertIdea(author.userId(), 100);
        UUID first = insertLot(ideaId, owner.userId(), 4, NOW.minusSeconds(86_400));
        UUID second = insertLot(ideaId, owner.userId(), 2, NOW.minusSeconds(86_400));

        recover(owner.token(), first, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.walletPaidAmount").value(400));
        recover(owner.token(), second, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.walletPaidAmount").value(100))
                .andExpect(jsonPath("$.pendingAmount").value(100));
    }

    @Test
    void concurrentRetriesCreateOneRecoveryAndOneCredit() throws Exception {
        Session owner = signupAndLogin("race-owner@example.com", "race_owner");
        Session author = signupAndLogin("race-author@example.com", "race_author");
        UUID ideaId = insertIdea(author.userId(), 10);
        UUID lotId = insertLot(ideaId, owner.userId(), 10, NOW.minusSeconds(86_400));

        List<MvcResult> results = concurrentRecoveries(owner.token(), lotId);

        assertThat(results).allMatch(result -> result.getResponse().getStatus() == 200);
        assertThat(results.stream().map(this::recoveryId).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seed_unit_recoveries WHERE lot_id=?", Integer.class, lotId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM point_ledgers WHERE source_type='UNIT_RECOVERY' AND source_id=?",
                Integer.class, lotId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM point_wallets WHERE user_id=?", Integer.class, owner.userId()))
                .isEqualTo(430);
    }

    @Test
    void publishesRecoveryOpenApiAndRequiresAuthentication() throws Exception {
        UUID randomLot = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/unit-lots/{lotId}/recover", randomLot))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/unit-lots/{lotId}/recover'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/unit-lots/{lotId}/recover'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/unit-lots/{lotId}/recover'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/unit-lots/{lotId}/recover'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/unit-lots/{lotId}/recover'].post.responses['409']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions recover(String token, UUID lotId, String body)
            throws Exception {
        var request = post("/api/v1/unit-lots/{lotId}/recover", lotId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private List<MvcResult> concurrentRecoveries(String token, UUID lotId) throws Exception {
        int count = 4;
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<MvcResult>>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return recover(token, lotId, null).andReturn();
                }));
            }
            ready.await();
            start.countDown();
            var results = new ArrayList<MvcResult>();
            for (var future : futures) results.add(future.get());
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private String recoveryId(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("recoveryId").asText();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID insertIdea(UUID authorId, int price) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ideas(id, author_id, status, title, category, summary, problem, target_customer,
                                  solution, business_model, visibility, current_unit_price, published_at,
                                  created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', '회수 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델',
                        'PUBLIC', ?, ?, ?, ?)
                """, id, authorId, price, Timestamp.from(NOW.minusSeconds(172_800)),
                Timestamp.from(NOW.minusSeconds(172_800)), Timestamp.from(NOW.minusSeconds(172_800)));
        return id;
    }

    private UUID insertLot(UUID ideaId, UUID userId, int units, Instant purchasedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO seed_unit_lots(id, idea_id, user_id, units, purchase_price, principal,
                                           purchase_request_key, purchased_at, unlocked_at, status)
                VALUES (?, ?, ?, ?, 10, ?, ?, ?, ?, 'LOCKED')
                """, id, ideaId, userId, units, units * 10, "recovery-fixture-" + id,
                Timestamp.from(purchasedAt), Timestamp.from(purchasedAt.plusSeconds(86_400)));
        return id;
    }

    private Session signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("accessToken").asText();
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return new Session(token, UUID.fromString(objectMapper.readTree(payload).get("sub").asText()));
    }

    private String bearer(String token) { return "Bearer " + token; }

    record Session(String token, UUID userId) {}

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneId.of("Asia/Seoul")); }
    }
}
