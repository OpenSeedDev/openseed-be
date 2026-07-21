package com.seedrank.ai.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AiCandidateDraftRequest(
        @Min(1) @Max(5) int candidateNumber,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 200) String summary,
        @NotBlank @Size(max = 2000) String problem,
        @NotBlank @Size(max = 1000) String targetCustomer,
        @NotBlank @Size(max = 2000) String solution,
        @NotBlank @Size(max = 2000) String businessModel) {
}
