package com.seedrank.idea.question;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ValidationQuestionRequest(
        @NotNull
        @Size(min = 1, max = 3)
        @ArraySchema(minItems = 1, maxItems = 3, schema = @Schema(description = "검증 질문"))
        List<@NotBlank String> questions) {
}
