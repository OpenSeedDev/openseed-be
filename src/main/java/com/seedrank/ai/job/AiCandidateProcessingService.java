package com.seedrank.ai.job;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
class AiCandidateProcessingService {

    private final AiJobWorkerService worker;
    private final AiCandidateProvider provider;
    private final AiCandidateResponseValidator validator;

    AiCandidateProcessingService(
            AiJobWorkerService worker,
            ObjectProvider<AiCandidateProvider> provider,
            AiCandidateResponseValidator validator) {
        this.worker = worker;
        this.provider = provider.getIfAvailable(() -> (input, prompt) -> {
            throw new AiProviderException(AiJobFailure.SERVER_ERROR);
        });
        this.validator = validator;
    }

    AiCandidateProcessingOutcome processNext(String workerId) {
        var claim = worker.claimNext(workerId);
        if (claim.isEmpty()) return AiCandidateProcessingOutcome.NO_JOB;

        AiJobClaim job = claim.orElseThrow();
        final String rawResult;
        try {
            rawResult = provider.generate(job.inputSnapshot(), job.promptVersion());
        } catch (AiProviderException exception) {
            boolean retryScheduled = worker.scheduleRetry(job.jobId(), job.leaseToken(), exception.failure());
            return retryScheduled
                    ? AiCandidateProcessingOutcome.RETRY_SCHEDULED
                    : AiCandidateProcessingOutcome.FAILED_FINAL;
        }

        final String normalizedResult;
        try {
            normalizedResult = validator.validateAndNormalize(rawResult);
        } catch (InvalidAiCandidateResponseException exception) {
            worker.failInvalidResponse(job.jobId(), job.leaseToken());
            return AiCandidateProcessingOutcome.FAILED_INVALID_RESPONSE;
        }

        worker.complete(job.jobId(), job.leaseToken(), rawResult, normalizedResult);
        return AiCandidateProcessingOutcome.SUCCEEDED;
    }
}
