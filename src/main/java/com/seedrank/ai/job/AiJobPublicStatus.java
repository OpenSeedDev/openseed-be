package com.seedrank.ai.job;

public enum AiJobPublicStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED;

    static AiJobPublicStatus from(AiJobStatus status) {
        return switch (status) {
            case PENDING, RETRY_WAIT -> PENDING;
            case PROCESSING -> PROCESSING;
            case SUCCEEDED -> SUCCEEDED;
            case FAILED -> FAILED;
        };
    }
}
