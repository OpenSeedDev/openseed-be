package com.seedrank.ai.job;

import java.time.Instant;
import java.util.UUID;

public record AiJobResultResponse(
        UUID jobId,
        AiJobPublicStatus status,
        AiCandidateResult result,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {
}
