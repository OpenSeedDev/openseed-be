package com.seedrank.unit.consistency;

import java.util.UUID;

record FinancialConsistencyFinding(
        String code,
        UUID userId,
        String entityType,
        UUID entityId,
        Long expectedValue,
        Long actualValue) {
}
