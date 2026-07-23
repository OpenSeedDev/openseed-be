package com.seedrank.unit.recovery.payout;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.point.PointLedger;
import com.seedrank.point.PointLedgerRepository;
import com.seedrank.point.PointWallet;
import com.seedrank.point.PointWalletRepository;

@Service
class PendingRecoveryPayoutService {
    private static final int DAILY_RECOVERY_PAYMENT_CAP = 500;
    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");

    private final AccessTokenAuthenticator authenticator;
    private final PointWalletRepository wallets;
    private final PointLedgerRepository ledgers;
    private final PendingRecoveryPayoutRepository payouts;
    private final Clock clock;

    PendingRecoveryPayoutService(
            AccessTokenAuthenticator authenticator,
            PointWalletRepository wallets,
            PointLedgerRepository ledgers,
            PendingRecoveryPayoutRepository payouts,
            Clock clock) {
        this.authenticator = authenticator;
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.payouts = payouts;
        this.clock = clock;
    }

    @Transactional
    PendingRecoveryPayoutResponse payout(String authorization) {
        UUID userId = authenticator.authenticate(authorization).userId();
        PointWallet wallet = wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Point wallet not found"));
        Instant now = clock.instant();
        LocalDate policyDate = now.atZone(POLICY_ZONE).toLocalDate();
        Instant dayStart = policyDate.atStartOfDay(POLICY_ZONE).toInstant();
        Instant nextDayStart = policyDate.plusDays(1).atStartOfDay(POLICY_ZONE).toInstant();
        long alreadyPaid = payouts.sumInitialRecoveryPaidAmount(userId, dayStart, nextDayStart)
                + payouts.sumPaidAmount(userId, dayStart, nextDayStart);
        int dailyCapacity = Math.max(0, DAILY_RECOVERY_PAYMENT_CAP - Math.toIntExact(alreadyPaid));
        int paidAmount = wallet.payoutPendingRecovery(dailyCapacity, now);
        if (paidAmount == 0) {
            return PendingRecoveryPayoutResponse.unchanged(
                    wallet.getBalance(), wallet.getPendingRecoveryBalance(), policyDate, now);
        }
        PendingRecoveryPayout payout = payouts.save(PendingRecoveryPayout.create(
                wallet.getUser(), paidAmount, wallet.getBalance(), policyDate, now));
        ledgers.save(PointLedger.pendingRecoveryPayout(
                wallet.getUser(), payout.id(), paidAmount, wallet.getBalance(), policyDate, now));
        return PendingRecoveryPayoutResponse.paid(payout, wallet.getPendingRecoveryBalance());
    }
}
