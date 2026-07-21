package com.seedrank.unit.purchase;

import jakarta.validation.constraints.Min;

record SeedUnitPurchaseRequest(
        @Min(value = 1, message = "Unit은 양의 정수여야 합니다.") int units,
        @Min(value = 1, message = "확인 가격은 양수여야 합니다.") int confirmedUnitPrice) {
}
