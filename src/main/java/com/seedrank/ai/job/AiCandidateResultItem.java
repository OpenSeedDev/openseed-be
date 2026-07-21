package com.seedrank.ai.job;

public record AiCandidateResultItem(
        String title,
        String category,
        String summary,
        String problem,
        String targetCustomer,
        String solution,
        String businessModel) {
}
