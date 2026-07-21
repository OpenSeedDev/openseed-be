package com.seedrank.ai.job;

class InvalidAiCandidateResponseException extends RuntimeException {
    InvalidAiCandidateResponseException() {
        super("AI candidate response does not match the required schema");
    }
}
