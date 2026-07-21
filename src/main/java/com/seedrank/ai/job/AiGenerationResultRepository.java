package com.seedrank.ai.job;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AiGenerationResultRepository extends JpaRepository<AiGenerationResult, UUID> {
    Optional<AiGenerationResult> findByAiJobId(UUID aiJobId);
}
