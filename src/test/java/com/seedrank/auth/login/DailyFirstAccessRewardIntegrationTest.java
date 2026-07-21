package com.seedrank.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.seedrank.TestcontainersConfiguration;
import com.seedrank.member.UserRepository;
import com.seedrank.point.ActivityReward;
import com.seedrank.point.PointRewardService;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, DailyFirstAccessRewardIntegrationTest.ClockTestConfiguration.class})
@SpringBootTest(properties = {
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.auth.cookie-secure=false"
})
@AutoConfigureMockMvc
class DailyFirstAccessRewardIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PointRewardService rewards;
    @Autowired MutableClock clock;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @AfterEach
    void dropFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_daily_reward_insert ON point_ledgers");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_daily_reward_insert()");
    }

    @Test
    void firstSuccessfulLoginOfPolicyDateRewardsOneHundredPoints() throws Exception {
        signup();

        mockMvc.perform(login("password123")).andExpect(status().isOk());

        assertThat(balance()).isEqualTo(400);
        assertThat(jdbc.queryForMap("""
                SELECT original_amount, paid_amount, expired_amount, policy_date
                FROM point_ledgers WHERE source_type='DAILY_FIRST_ACCESS'
                """))
                .containsEntry("original_amount", 100)
                .containsEntry("paid_amount", 100)
                .containsEntry("expired_amount", 0)
                .containsEntry("policy_date", java.sql.Date.valueOf("2026-01-01"));
    }

    @Test
    void repeatedLoginsOnSamePolicyDateDoNotRewardAgain() throws Exception {
        signup();

        mockMvc.perform(login("password123")).andExpect(status().isOk());
        mockMvc.perform(login("password123")).andExpect(status().isOk());

        assertThat(balance()).isEqualTo(400);
        assertThat(dailyRewardCount()).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isEqualTo(2);
    }

    @Test
    void rewardsAgainAfterAsiaSeoulMidnight() throws Exception {
        signup();
        clock.set(Instant.parse("2026-01-01T14:59:59Z"));
        mockMvc.perform(login("password123")).andExpect(status().isOk());

        clock.set(Instant.parse("2026-01-01T15:00:00Z"));
        mockMvc.perform(login("password123")).andExpect(status().isOk());

        assertThat(balance()).isEqualTo(500);
        assertThat(jdbc.queryForList("""
                SELECT policy_date FROM point_ledgers
                WHERE source_type='DAILY_FIRST_ACCESS' ORDER BY policy_date
                """, java.time.LocalDate.class))
                .containsExactly(java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-01-02"));
    }

    @Test
    void invalidCredentialsDoNotCreateDailyReward() throws Exception {
        signup();

        mockMvc.perform(login("wrong-password")).andExpect(status().isUnauthorized());

        assertThat(balance()).isEqualTo(300);
        assertThat(dailyRewardCount()).isZero();
    }

    @Test
    void appliesDailyActivityCapAndRecordsExpiredAmount() throws Exception {
        signup();
        UUID userId = users.findByEmail("member@example.com").orElseThrow().getId();
        rewards.grant(userId, ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());
        rewards.grant(userId, ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());
        rewards.grant(userId, ActivityReward.IDEA_PUBLISHED, UUID.randomUUID());

        mockMvc.perform(login("password123")).andExpect(status().isOk());

        assertThat(balance()).isEqualTo(600);
        assertThat(jdbc.queryForMap("""
                SELECT original_amount, paid_amount, expired_amount
                FROM point_ledgers WHERE source_type='DAILY_FIRST_ACCESS'
                """))
                .containsEntry("original_amount", 100)
                .containsEntry("paid_amount", 50)
                .containsEntry("expired_amount", 50);
    }

    @Test
    void concurrentSuccessfulLoginsRewardOnlyOnce() throws Exception {
        signup();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> loginAfterLatch(ready, start));
            Future<Integer> second = executor.submit(() -> loginAfterLatch(ready, start));
            ready.await();
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get())).containsOnly(200);
        }
        assertThat(balance()).isEqualTo(400);
        assertThat(dailyRewardCount()).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isEqualTo(2);
    }

    @Test
    void pointRewardFailureRollsBackTheLoginSession() throws Exception {
        signup();
        jdbc.execute("""
                CREATE FUNCTION reject_daily_reward_insert() RETURNS trigger AS $$
                BEGIN
                    IF NEW.source_type = 'DAILY_FIRST_ACCESS' THEN RAISE EXCEPTION 'forced'; END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_daily_reward_insert BEFORE INSERT ON point_ledgers
                FOR EACH ROW EXECUTE FUNCTION reject_daily_reward_insert()
                """);

        mockMvc.perform(login("password123")).andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isZero();
        assertThat(balance()).isEqualTo(300);
        assertThat(dailyRewardCount()).isZero();
    }

    private int loginAfterLatch(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(login("password123")).andReturn().getResponse().getStatus();
    }

    private void signup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member@example.com","password":"password123","profileId":"open_seed"}
                                """))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"member@example.com","password":"%s"}
                        """.formatted(password));
    }

    private int balance() {
        return jdbc.queryForObject("SELECT balance FROM point_wallets", Integer.class);
    }

    private int dailyRewardCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM point_ledgers WHERE source_type='DAILY_FIRST_ACCESS'", Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockTestConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant = Instant.EPOCH;

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneId.of("Asia/Seoul").equals(zone)) {
                throw new IllegalArgumentException("The policy clock must use Asia/Seoul");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
