package com.seedrank.company.activity;

import java.time.Instant;
import java.util.UUID;

public record CompanyInquiryActivityResponse(
        UUID threadId,
        UUID ideaId,
        String ideaTitle,
        Instant updatedAt) {
}
