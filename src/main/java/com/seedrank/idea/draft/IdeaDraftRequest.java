package com.seedrank.idea.draft;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdeaDraftRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 50) String category,
        @Size(max = 200) String summary,
        @NotBlank @Size(max = 2000) String problem,
        @Size(max = 1000) String targetCustomer,
        @Size(max = 2000) String solution,
        @Size(max = 2000) String businessModel) {
}
