package com.seedrank.unit.purchase;

import jakarta.validation.constraints.Min;

record SeedUnitPreviewRequest(
        @Min(value = 1, message = "Unit은 양의 정수여야 합니다.") int units) {
}
