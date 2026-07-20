package com.seedrank.auth.signup;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupRequest(
        @Schema(example = "member@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @Schema(example = "password123", accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password,

        @Schema(example = "open_seed", pattern = "^[A-Za-z0-9_]{3,20}$",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String profileId) {
}
