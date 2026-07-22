package com.seedrank.ai.job;

enum AiCandidateProcessingOutcome {
    NO_JOB,
    SUCCEEDED,
    RETRY_SCHEDULED,
    FAILED_FINAL,
    FAILED_INVALID_RESPONSE
}
