package com.seedrank.ai.job;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AiGenerationResultRepository extends JpaRepository<AiGenerationResult, UUID> {
}
