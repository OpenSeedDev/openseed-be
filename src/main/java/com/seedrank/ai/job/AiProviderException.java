package com.seedrank.ai.job;

class AiProviderException extends RuntimeException {
    private final AiJobFailure failure;

    AiProviderException(AiJobFailure failure) {
        super("AI provider request failed: " + failure);
        if (failure == null) throw new IllegalArgumentException("AI provider failure is required");
        this.failure = failure;
    }

    AiJobFailure failure() {
        return failure;
    }
}
