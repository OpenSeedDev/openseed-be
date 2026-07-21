package com.seedrank.unit.purchase;

import java.time.Instant;
import java.util.UUID;

record SeedUnitPreviewResponse(
        UUID ideaId,
        int units,
        int unitPrice,
        int totalPoint,
        int expectedBalance,
        int lockedForHours,
        Instant estimatedUnlockedAt,
        String nonMonetaryNotice) {
}
