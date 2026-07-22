package com.seedrank.unit.recovery;

import java.time.Instant;
import java.util.UUID;

record SeedUnitRecoveryResponse(
        UUID recoveryId,
        UUID lotId,
        UUID ideaId,
        int units,
        int recoveryPrice,
        int realizedAmount,
        int walletPaidAmount,
        int pendingAmount,
        int balanceAfter,
        int pendingRecoveryBalance,
        Instant recoveredAt,
        String nonMonetaryNotice) {

    static SeedUnitRecoveryResponse from(
            SeedUnitRecovery recovery, int balanceAfter, int pendingRecoveryBalance, String notice) {
        return new SeedUnitRecoveryResponse(recovery.id(), recovery.lotId(), recovery.ideaId(), recovery.units(),
                recovery.recoveryPrice(), recovery.realizedAmount(), recovery.walletPaidAmount(),
                recovery.pendingAmount(), balanceAfter, pendingRecoveryBalance, recovery.createdAt(), notice);
    }
}
