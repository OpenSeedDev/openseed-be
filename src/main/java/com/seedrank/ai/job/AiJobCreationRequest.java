package com.seedrank.ai.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AiJobCreationRequest(
        @NotBlank @Size(max = 200) String keyword,
        @NotBlank @Size(max = 2000) String background) {
}
