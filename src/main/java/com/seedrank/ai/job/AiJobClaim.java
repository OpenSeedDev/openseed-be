package com.seedrank.ai.job;

import java.time.Instant;
import java.util.UUID;

record AiJobClaim(
        UUID jobId,
        String inputSnapshot,
        String promptVersion,
        int retryCount,
        UUID leaseToken,
        Instant lockedUntil) {
}
