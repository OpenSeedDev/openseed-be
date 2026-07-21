package com.seedrank.ai.job;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    Optional<AiJob> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

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
