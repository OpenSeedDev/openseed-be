package com.seedrank.idea.publish;

import com.seedrank.idea.IdeaVisibility;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record IdeaPublishRequest(
        @NotNull
        @Schema(allowableValues = {"PUBLIC", "SEMI_PUBLIC", "MATCHING"})
        IdeaVisibility visibility) {
}
