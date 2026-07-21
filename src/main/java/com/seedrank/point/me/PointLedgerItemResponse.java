package com.seedrank.point.me;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.point.PointLedger;

record PointLedgerItemResponse(
        UUID id,
        PointLedger.Type type,
        int originalAmount,
        int paidAmount,
        int expiredAmount,
        int balanceAfter,
        PointLedger.SourceType sourceType,
        UUID sourceId,
        Instant createdAt) {

    static PointLedgerItemResponse from(PointLedger ledger) {
        return new PointLedgerItemResponse(
                ledger.getId(),
                ledger.getType(),
                ledger.getOriginalAmount(),
                ledger.getPaidAmount(),
                ledger.getExpiredAmount(),
                ledger.getBalanceAfter(),
                ledger.getSourceType(),
                ledger.getSourceId(),
                ledger.getCreatedAt());
    }
}
