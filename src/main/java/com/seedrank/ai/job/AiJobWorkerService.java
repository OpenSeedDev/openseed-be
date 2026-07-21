package com.seedrank.ai.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AiJobWorkerService {
    private static final long LEASE_SECONDS = 120;
    private static final long BASE_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 15 * 60;

    private final AiJobRepository jobs;
    private final Clock clock;

    AiJobWorkerService(AiJobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    Optional<AiJobClaim> claimNext(String workerId) {
        String validatedWorkerId = validateWorkerId(workerId);
        Instant now = clock.instant();
        return jobs.findNextClaimableForUpdate(now)
                .map(job -> job.claim(validatedWorkerId, now, now.plusSeconds(LEASE_SECONDS)));
    }

    @Transactional
    void scheduleRetry(UUID jobId, UUID leaseToken, AiJobFailure failure) {
        if (jobId == null || leaseToken == null || failure == null) {
            throw new IllegalArgumentException("Job retry requires job, lease token and failure");
        }
        AiJob job = jobs.findByIdForUpdate(jobId).orElseThrow(StaleAiJobLeaseException::new);
        Instant now = clock.instant();
        job.scheduleRetry(leaseToken, now.plusSeconds(backoffSeconds(job.retryCount() + 1)), now);
    }

    private long backoffSeconds(int retryCount) {
        long delay = BASE_BACKOFF_SECONDS;
        for (int attempt = 1; attempt < retryCount && delay < MAX_BACKOFF_SECONDS; attempt++) {
            delay = Math.min(delay * 2, MAX_BACKOFF_SECONDS);
        }
        return delay;
    }

    private String validateWorkerId(String workerId) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 100
                || !workerId.equals(workerId.strip()) || workerId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid AI worker id");
        }
        return workerId;
    }
}
