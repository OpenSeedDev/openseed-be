package com.seedrank.unit.consistency;

import java.util.UUID;

record FinancialConsistencyCheckResult(
        UUID checkId,
        FinancialConsistencyStatus status,
        int findingCount) {
}
