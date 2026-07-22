package com.seedrank.unit.recovery.payout;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record PendingRecoveryPayoutResponse(
        UUID payoutId,
        int paidAmount,
        int balanceAfter,
        int pendingRecoveryBalance,
        LocalDate policyDate,
        Instant paidAt) {

    static PendingRecoveryPayoutResponse paid(PendingRecoveryPayout payout, int pendingRecoveryBalance) {
        return new PendingRecoveryPayoutResponse(
                payout.id(), payout.paidAmount(), payout.balanceAfter(), pendingRecoveryBalance,
                payout.policyDate(), payout.paidAt());
    }

    static PendingRecoveryPayoutResponse unchanged(
            int balanceAfter, int pendingRecoveryBalance, LocalDate policyDate, Instant now) {
        return new PendingRecoveryPayoutResponse(
                null, 0, balanceAfter, pendingRecoveryBalance, policyDate, now);
    }
}
