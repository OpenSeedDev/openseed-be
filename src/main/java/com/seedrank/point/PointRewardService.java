package com.seedrank.point;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointRewardService {

    static final int DAILY_ACTIVITY_CAP = 300;
    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");

    private final PointWalletRepository wallets;
    private final PointLedgerRepository ledgers;
    private final Clock clock;

    public PointRewardService(
            PointWalletRepository wallets,
            PointLedgerRepository ledgers,
            Clock clock) {
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.clock = clock;
    }

    @Transactional
    public PointRewardResult grant(UUID userId, ActivityReward reward, UUID sourceId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(reward, "reward");
        Objects.requireNonNull(sourceId, "sourceId");

        Instant now = clock.instant();
        LocalDate policyDate = policyDate(now);
        return grant(userId, reward, sourceId, now, policyDate);
    }

    @Transactional
    public PointRewardResult grantDailyFirstAccess(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        Instant now = clock.instant();
        LocalDate policyDate = policyDate(now);
        UUID sourceId = UUID.nameUUIDFromBytes(
                ("daily-first-access:" + userId + ":" + policyDate).getBytes(StandardCharsets.UTF_8));
        return grant(userId, ActivityReward.DAILY_FIRST_ACCESS, sourceId, now, policyDate);
    }

    private PointRewardResult grant(
            UUID userId,
            ActivityReward reward,
            UUID sourceId,
            Instant now,
            LocalDate policyDate) {
        PointWallet wallet = wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Point wallet not found"));
        var duplicate = ledgers.findBySourceTypeAndSourceId(reward.sourceType(), sourceId);
        if (duplicate.isPresent()) {
            return PointRewardResult.duplicate(duplicate.orElseThrow());
        }

        int paidToday = Math.toIntExact(ledgers.sumPaidActivityRewards(userId, policyDate));
        int dailyAllowance = Math.max(0, DAILY_ACTIVITY_CAP - paidToday);
        int requestedPayment = Math.min(reward.amount(), dailyAllowance);
        int paidAmount = wallet.credit(requestedPayment, now);

        PointLedger ledger = PointLedger.activityReward(
                wallet.getUser(),
                reward.sourceType(),
                sourceId,
                reward.amount(),
                paidAmount,
                wallet.getBalance(),
                policyDate,
                now);
        ledgers.saveAndFlush(ledger);
        return PointRewardResult.created(ledger);
    }

    private LocalDate policyDate(Instant now) {
        return now.atZone(POLICY_ZONE).toLocalDate();
    }
}
