package com.seedrank.ai.job;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seedrank.auth.login.AccessTokenAuthenticator;

@Service
class AiJobResultQueryService {
    private final AccessTokenAuthenticator authenticator;
    private final AiJobRepository jobs;
    private final AiGenerationResultRepository results;
    private final JsonMapper json = JsonMapper.builder().build();

    AiJobResultQueryService(
            AccessTokenAuthenticator authenticator,
            AiJobRepository jobs,
            AiGenerationResultRepository results) {
        this.authenticator = authenticator;
        this.jobs = jobs;
        this.results = results;
    }

    @Transactional(readOnly = true)
    AiJobResultResponse get(String authorization, UUID jobId) {
        UUID ownerId = authenticator.authenticate(authorization).userId();
        if (jobId == null) throw new AiJobNotFoundException();
        AiJob job = jobs.findByIdAndOwnerId(jobId, ownerId).orElseThrow(AiJobNotFoundException::new);
        AiJobPublicStatus publicStatus = AiJobPublicStatus.from(job.status());
        AiCandidateResult result = publicStatus == AiJobPublicStatus.SUCCEEDED ? result(job.id()) : null;
        String failureCode = publicStatus == AiJobPublicStatus.FAILED ? job.failureCode() : null;
        AiJobManualForm manualForm = publicStatus == AiJobPublicStatus.FAILED ? AiJobManualForm.empty() : null;
        return new AiJobResultResponse(
                job.id(), publicStatus, result, failureCode, manualForm, job.createdAt(), job.updatedAt());
    }

    private AiCandidateResult result(UUID jobId) {
        String normalized = results.findByAiJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("Succeeded AI Job result is missing"))
                .normalizedResult();
        try {
            return json.readValue(normalized, AiCandidateResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored AI Job result is invalid", exception);
        }
    }
}
