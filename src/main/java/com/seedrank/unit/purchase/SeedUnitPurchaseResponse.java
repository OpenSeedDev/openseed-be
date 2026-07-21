package com.seedrank.unit.purchase;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.unit.SeedUnitLot;

record SeedUnitPurchaseResponse(
        UUID lotId,
        UUID ideaId,
        int units,
        int purchasePrice,
        int principal,
        int balanceAfter,
        Instant purchasedAt,
        Instant unlockedAt,
        SeedUnitLot.Status status,
        String nonMonetaryNotice) {

    static SeedUnitPurchaseResponse from(SeedUnitLot lot, int balanceAfter, String notice) {
        return new SeedUnitPurchaseResponse(lot.id(), lot.ideaId(), lot.units(), lot.purchasePrice(),
                lot.principal(), balanceAfter, lot.purchasedAt(), lot.unlockedAt(), lot.status(), notice);
    }
}
