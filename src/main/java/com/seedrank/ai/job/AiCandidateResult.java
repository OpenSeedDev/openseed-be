package com.seedrank.ai.job;

import java.util.List;

public record AiCandidateResult(
        String problemAnalysis,
        List<AiCandidateResultItem> candidates) {
}
