package com.seedrank.ai.job;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    Optional<AiJob> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

    @Query(value = """
            SELECT * FROM ai_jobs
            WHERE status = 'PENDING'
               OR (status = 'RETRY_WAIT' AND next_attempt_at <= :now)
               OR (status = 'PROCESSING' AND locked_until <= :now)
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<AiJob> findNextClaimableForUpdate(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AiJob job where job.id=:jobId")
    Optional<AiJob> findByIdForUpdate(@Param("jobId") UUID jobId);

    @Modifying
    @Query(value = """
            INSERT INTO ai_jobs (
                id, owner_id, status, input_snapshot, prompt_version, idempotency_key,
                retry_count, locked_until, created_at, updated_at
            ) VALUES (
                :id, :ownerId, 'PENDING', CAST(:inputSnapshot AS jsonb), :promptVersion, :idempotencyKey,
                0, NULL, :now, :now
            )
            ON CONFLICT (owner_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId,
            @Param("inputSnapshot") String inputSnapshot,
            @Param("promptVersion") String promptVersion,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("now") Instant now);
}
