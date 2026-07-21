package com.seedrank.unit.me;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.unit.SeedUnitLot;

record OwnedUnitLotItemResponse(
        UUID lotId,
        UUID ideaId,
        String ideaTitle,
        int units,
        int purchasePrice,
        int principal,
        int currentUnitPrice,
        int currentValue,
        int pointDifference,
        int activeUnitsForIdea,
        Instant purchasedAt,
        Instant unlockedAt,
        boolean recoveryAvailable,
        SeedUnitLot.Status status) {

    static OwnedUnitLotItemResponse from(SeedUnitLot lot, int activeUnitsForIdea, Instant now) {
        int currentUnitPrice = lot.currentUnitPrice();
        int currentValue = Math.multiplyExact(lot.units(), currentUnitPrice);
        return new OwnedUnitLotItemResponse(
                lot.id(), lot.ideaId(), lot.ideaTitle(), lot.units(), lot.purchasePrice(), lot.principal(),
                currentUnitPrice, currentValue, currentValue - lot.principal(), activeUnitsForIdea,
                lot.purchasedAt(), lot.unlockedAt(), !now.isBefore(lot.unlockedAt()), lot.status());
    }
}
