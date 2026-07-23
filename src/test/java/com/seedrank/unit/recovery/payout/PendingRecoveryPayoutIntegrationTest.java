package com.seedrank.unit.recovery.payout;

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
@Import({TestcontainersConfiguration.class, PendingRecoveryPayoutIntegrationTest.FixedClockConfig.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class PendingRecoveryPayoutIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-22T15:30:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM pending_recovery_payouts");
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
    void paysTheFrozenPendingBalanceWithoutRepricing() throws Exception {
        Session user = signupAndLogin("pending@example.com", "pending_user");
        setWallet(user.userId(), 300, 250);

        payout(user.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutId").isNotEmpty())
                .andExpect(jsonPath("$.paidAmount").value(250))
                .andExpect(jsonPath("$.balanceAfter").value(550))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(0))
                .andExpect(jsonPath("$.policyDate").value("2026-07-23"))
                .andExpect(jsonPath("$.paidAt").value(NOW.toString()));

        assertThat(payoutRows(user.userId())).isEqualTo(1);
        assertThat(payoutLedgerAmount(user.userId())).isEqualTo(250);
    }

    @Test
    void includesTheOriginalRecoveryPaymentInTheDailyFiveHundredPointCap() throws Exception {
        Session user = signupAndLogin("daily-pending@example.com", "daily_pending");
        Session author = signupAndLogin("daily-author@example.com", "daily_author_2");
        setWallet(user.userId(), 300, 300);
        insertRecovery(user.userId(), author.userId(), 400, 300, NOW.minusSeconds(60));

        payout(user.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(100))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(200));
        payout(user.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutId").doesNotExist())
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(200));

        assertThat(payoutRows(user.userId())).isEqualTo(1);
    }

    @Test
    void paysOnlyTheAvailableWalletCapacityAndKeepsTheRestPending() throws Exception {
        Session user = signupAndLogin("wallet-cap@example.com", "wallet_cap_user");
        setWallet(user.userId(), 1_950, 300);

        payout(user.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(50))
                .andExpect(jsonPath("$.balanceAfter").value(2_000))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(250));
    }

    @Test
    void previousAsiaSeoulPolicyDateDoesNotConsumeTodaysLimit() throws Exception {
        Session user = signupAndLogin("next-policy-date@example.com", "next_policy_date");
        setWallet(user.userId(), 300, 500);
        jdbc.update("""
                INSERT INTO pending_recovery_payouts(id, user_id, paid_amount, balance_after, policy_date, paid_at)
                VALUES (?, ?, 500, 800, '2026-07-22', ?)
                """, UUID.randomUUID(), user.userId(), Timestamp.from(Instant.parse("2026-07-22T14:59:59Z")));

        payout(user.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(500))
                .andExpect(jsonPath("$.policyDate").value("2026-07-23"))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(0));
    }

    @Test
    void zeroPayableRequestHasNoPayoutOrLedgerSideEffect() throws Exception {
        Session user = signupAndLogin("no-pending@example.com", "no_pending_user");

        payout(user.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutId").doesNotExist())
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.balanceAfter").value(330))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(0));

        assertThat(payoutRows(user.userId())).isZero();
        assertThat(payoutLedgerAmount(user.userId())).isZero();
    }

    @Test
    void concurrentRequestsNeverPayMoreThanThePendingBalance() throws Exception {
        Session user = signupAndLogin("pending-race@example.com", "pending_race");
        setWallet(user.userId(), 300, 500);

        List<MvcResult> results = concurrentPayouts(user.token());

        assertThat(results).allMatch(result -> result.getResponse().getStatus() == 200);
        assertThat(results.stream().mapToInt(this::paidAmount).sum()).isEqualTo(500);
        assertThat(payoutRows(user.userId())).isEqualTo(1);
        assertThat(payoutLedgerAmount(user.userId())).isEqualTo(500);
        assertThat(jdbc.queryForObject(
                "SELECT pending_recovery_balance FROM point_wallets WHERE user_id=?", Integer.class, user.userId()))
                .isZero();
    }

    @Test
    void publishesAuthenticatedPayoutOpenApi() throws Exception {
        mockMvc.perform(post("/api/v1/me/pending-recovery/payout"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/me/pending-recovery/payout'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/pending-recovery/payout'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/pending-recovery/payout'].post.responses['401']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions payout(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/me/pending-recovery/payout")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private List<MvcResult> concurrentPayouts(String token) throws Exception {
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
                    return payout(token).andReturn();
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

    private int paidAmount(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("paidAmount").asInt();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int payoutRows(UUID userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM pending_recovery_payouts WHERE user_id=?", Integer.class, userId);
    }

    private int payoutLedgerAmount(UUID userId) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(paid_amount), 0) FROM point_ledgers
                WHERE user_id=? AND source_type='PENDING_RECOVERY_PAYOUT'
                """, Integer.class, userId);
    }

    private void setWallet(UUID userId, int balance, int pending) {
        jdbc.update("UPDATE point_wallets SET balance=?, pending_recovery_balance=? WHERE user_id=?",
                balance, pending, userId);
    }

    private void insertRecovery(UUID userId, UUID authorId, int paid, int pending, Instant createdAt) {
        UUID ideaId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ideas(id, author_id, status, title, category, summary, problem, target_customer,
                                  solution, business_model, visibility, current_unit_price, published_at,
                                  created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', '대기 지급 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델',
                        'PUBLIC', 1, ?, ?, ?)
                """, ideaId, authorId, Timestamp.from(createdAt.minusSeconds(172_800)),
                Timestamp.from(createdAt.minusSeconds(172_800)), Timestamp.from(createdAt.minusSeconds(172_800)));
        jdbc.update("""
                INSERT INTO seed_unit_lots(id, idea_id, user_id, units, purchase_price, principal,
                                           purchase_request_key, purchased_at, unlocked_at, status)
                VALUES (?, ?, ?, 7, 10, 70, ?, ?, ?, 'RECOVERED')
                """, lotId, ideaId, userId, "payout-fixture-" + lotId,
                Timestamp.from(createdAt.minusSeconds(172_800)), Timestamp.from(createdAt.minusSeconds(86_400)));
        jdbc.update("""
                INSERT INTO seed_unit_recoveries(id, lot_id, user_id, idea_id, units, recovery_price,
                                                 realized_amount, wallet_paid_amount, pending_amount, created_at)
                VALUES (?, ?, ?, ?, 7, 100, 700, ?, ?, ?)
                """, UUID.randomUUID(), lotId, userId, ideaId, paid, pending, Timestamp.from(createdAt));
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
