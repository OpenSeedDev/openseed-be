package com.seedrank.point;

import java.util.UUID;

public record PointRewardResult(
        UUID ledgerId,
        int originalAmount,
        int paidAmount,
        int expiredAmount,
        int balanceAfter,
        boolean duplicate) {

    static PointRewardResult created(PointLedger ledger) {
        return from(ledger, false);
    }

    static PointRewardResult duplicate(PointLedger ledger) {
        return from(ledger, true);
    }

    private static PointRewardResult from(PointLedger ledger, boolean duplicate) {
        return new PointRewardResult(
                ledger.getId(),
                ledger.getOriginalAmount(),
                ledger.getPaidAmount(),
                ledger.getExpiredAmount(),
                ledger.getBalanceAfter(),
                duplicate);
    }
}
