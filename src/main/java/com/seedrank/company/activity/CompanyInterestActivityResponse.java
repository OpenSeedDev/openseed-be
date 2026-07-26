package com.seedrank.company.activity;

import java.time.Instant;
import java.util.UUID;

public record CompanyInterestActivityResponse(
        UUID ideaId,
        String title,
        Instant interestedAt) {
}
