package com.seedrank.ai.job;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_jobs")
class AiJob {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiJobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private String inputSnapshot;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiJob() {
    }

    UUID id() { return id; }
    UUID ownerId() { return ownerId; }
    AiJobStatus status() { return status; }
    String failureCode() { return failureCode; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    String inputSnapshot() { return inputSnapshot; }
    String promptVersion() { return promptVersion; }
    int retryCount() { return retryCount; }
    Instant lockedUntil() { return lockedUntil; }

    AiJobClaim claim(String workerId, Instant now, Instant leaseExpiresAt) {
        status = AiJobStatus.PROCESSING;
        leaseOwner = workerId;
        leaseToken = UUID.randomUUID();
        lockedUntil = leaseExpiresAt;
        nextAttemptAt = null;
        updatedAt = now;
        return new AiJobClaim(id, inputSnapshot, promptVersion, retryCount, leaseToken, lockedUntil);
    }

    void scheduleRetry(UUID claimedToken, Instant retryAt, Instant now) {
        verifyActiveLease(claimedToken, now);
        status = AiJobStatus.RETRY_WAIT;
        retryCount++;
        leaseOwner = null;
        leaseToken = null;
        lockedUntil = null;
        nextAttemptAt = retryAt;
        updatedAt = now;
    }

    void complete(UUID claimedToken, Instant now) {
        verifyActiveLease(claimedToken, now);
        status = AiJobStatus.SUCCEEDED;
        clearLease();
        failureCode = null;
        updatedAt = now;
    }

    void failInvalidResponse(UUID claimedToken, Instant now) {
        verifyActiveLease(claimedToken, now);
        status = AiJobStatus.FAILED;
        clearLease();
        failureCode = "INVALID_RESPONSE_SCHEMA";
        updatedAt = now;
    }

    private void verifyActiveLease(UUID claimedToken, Instant now) {
        if (status != AiJobStatus.PROCESSING || leaseToken == null || !leaseToken.equals(claimedToken)
                || lockedUntil == null || !lockedUntil.isAfter(now)) {
            throw new StaleAiJobLeaseException();
        }
    }

    private void clearLease() {
        leaseOwner = null;
        leaseToken = null;
        lockedUntil = null;
        nextAttemptAt = null;
    }
}
