package com.seedrank.ai.job;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_generation_results")
class AiGenerationResult {
    @Id
    private UUID id;

    @Column(name = "ai_job_id", nullable = false, unique = true)
    private UUID aiJobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_result", nullable = false, columnDefinition = "jsonb")
    private String rawResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_result", nullable = false, columnDefinition = "jsonb")
    private String normalizedResult;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiGenerationResult() {
    }

    private AiGenerationResult(UUID aiJobId, String rawResult, String normalizedResult, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.aiJobId = aiJobId;
        this.rawResult = rawResult;
        this.normalizedResult = normalizedResult;
        this.createdAt = createdAt;
    }

    static AiGenerationResult create(UUID aiJobId, String rawResult, String normalizedResult, Instant createdAt) {
        return new AiGenerationResult(aiJobId, rawResult, normalizedResult, createdAt);
    }
}
