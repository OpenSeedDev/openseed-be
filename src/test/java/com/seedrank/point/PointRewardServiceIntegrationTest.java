package com.seedrank.point;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.seedrank.TestcontainersConfiguration;
import com.seedrank.member.User;
import com.seedrank.member.UserRepository;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PointRewardServiceIntegrationTest.ClockTestConfiguration.class})
@SpringBootTest(properties = "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes")
class PointRewardServiceIntegrationTest {

    @Autowired PointRewardService rewards;
    @Autowired PointWalletRepository wallets;
    @Autowired PointLedgerRepository ledgers;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void grantsFixedActivityRewardsAndExcludesSignupBonusFromDailyCap() {
        User user = signupUser();

        PointRewardResult publish = rewards.grant(user.getId(), ActivityReward.IDEA_PUBLISHED, UUID.randomUUID());
        PointRewardResult feedback = rewards.grant(user.getId(), ActivityReward.FEEDBACK_CREATED, UUID.randomUUID());
        PointRewardResult accepted = rewards.grant(user.getId(), ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());

        assertThat(publish.originalAmount()).isEqualTo(50);
        assertThat(feedback.originalAmount()).isEqualTo(20);
        assertThat(accepted.originalAmount()).isEqualTo(100);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(470);
        assertThat(jdbc.queryForObject("""
                SELECT SUM(paid_amount) FROM point_ledgers
                WHERE user_id=? AND policy_date=DATE '2026-01-01'
                """, Integer.class, user.getId())).isEqualTo(170);
    }

    @Test
    void expiresOnlyTheAmountBeyondTheDailyActivityCap() {
        User user = signupUser();
        for (ActivityReward reward : java.util.List.of(
                ActivityReward.FEEDBACK_ACCEPTED,
                ActivityReward.FEEDBACK_ACCEPTED,
                ActivityReward.IDEA_PUBLISHED,
                ActivityReward.FEEDBACK_CREATED,
                ActivityReward.FEEDBACK_CREATED)) {
            rewards.grant(user.getId(), reward, UUID.randomUUID());
        }

        PointRewardResult capped = rewards.grant(
                user.getId(), ActivityReward.FEEDBACK_CREATED, UUID.randomUUID());

        assertThat(capped.paidAmount()).isEqualTo(10);
        assertThat(capped.expiredAmount()).isEqualTo(10);
        assertThat(capped.balanceAfter()).isEqualTo(600);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(600);
        assertThat(ledgers.findAll()).hasSize(7);
    }

    @Test
    void appliesWalletCapAndNeverMovesExpiredRewardToPendingRecovery() {
        User user = signupUser();
        jdbc.update("UPDATE point_wallets SET balance=1950 WHERE user_id=?", user.getId());

        PointRewardResult result = rewards.grant(
                user.getId(), ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());
        PointWallet wallet = wallets.findByUserId(user.getId()).orElseThrow();

        assertThat(result.originalAmount()).isEqualTo(100);
        assertThat(result.paidAmount()).isEqualTo(50);
        assertThat(result.expiredAmount()).isEqualTo(50);
        assertThat(result.balanceAfter()).isEqualTo(2000);
        assertThat(wallet.getBalance()).isEqualTo(2000);
        assertThat(wallet.getPendingRecoveryBalance()).isZero();
    }

    @Test
    void usesTheSmallerAllowanceWhenDailyAndWalletCapsBothApply() {
        User user = signupUser();
        rewards.grant(user.getId(), ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());
        rewards.grant(user.getId(), ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());
        jdbc.update("UPDATE point_wallets SET balance=1975 WHERE user_id=?", user.getId());

        PointRewardResult result = rewards.grant(
                user.getId(), ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());

        assertThat(result.paidAmount()).isEqualTo(25);
        assertThat(result.expiredAmount()).isEqualTo(75);
        assertThat(result.balanceAfter()).isEqualTo(2000);
    }

    @Test
    void treatsARepeatedSourceKeyAsAnIdempotentDuplicate() {
        User user = signupUser();
        UUID sourceId = UUID.randomUUID();

        PointRewardResult first = rewards.grant(user.getId(), ActivityReward.IDEA_PUBLISHED, sourceId);
        PointRewardResult repeated = rewards.grant(user.getId(), ActivityReward.IDEA_PUBLISHED, sourceId);

        assertThat(first.duplicate()).isFalse();
        assertThat(repeated.duplicate()).isTrue();
        assertThat(repeated.ledgerId()).isEqualTo(first.ledgerId());
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(350);
        assertThat(ledgers.findAll()).hasSize(2);
    }

    @Test
    void serializesConcurrentDuplicateRewards() throws Exception {
        User user = signupUser();
        UUID sourceId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<PointRewardResult> first = executor.submit(() -> grantAfterLatch(user.getId(), sourceId, ready, start));
            Future<PointRewardResult> second = executor.submit(() -> grantAfterLatch(user.getId(), sourceId, ready, start));
            ready.await();
            start.countDown();

            assertThat(java.util.List.of(first.get().duplicate(), second.get().duplicate()))
                    .containsExactlyInAnyOrder(false, true);
        }
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(400);
        assertThat(ledgers.findAll()).hasSize(2);
    }

    @Test
    void resetsDailyAllowanceAtAsiaSeoulMidnight() {
        User user = signupUser();
        clock.set(Instant.parse("2026-01-01T14:59:59Z"));
        rewards.grant(user.getId(), ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());
        clock.set(Instant.parse("2026-01-01T15:00:00Z"));

        PointRewardResult nextDay = rewards.grant(
                user.getId(), ActivityReward.FEEDBACK_ACCEPTED, UUID.randomUUID());

        assertThat(nextDay.paidAmount()).isEqualTo(100);
        assertThat(jdbc.queryForList("""
                SELECT policy_date FROM point_ledgers
                WHERE user_id=? AND policy_date IS NOT NULL ORDER BY policy_date
                """, java.time.LocalDate.class, user.getId()))
                .containsExactly(java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-01-02"));
    }

    private PointRewardResult grantAfterLatch(
            UUID userId, UUID sourceId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return rewards.grant(userId, ActivityReward.FEEDBACK_ACCEPTED, sourceId);
    }

    private User signupUser() {
        Instant now = clock.instant();
        User user = users.saveAndFlush(User.create(
                UUID.randomUUID() + "@example.com", "encoded-password", "seed_member", now));
        wallets.saveAndFlush(PointWallet.signupWallet(user, now));
        ledgers.saveAndFlush(PointLedger.signupBonus(user, now));
        return user;
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
