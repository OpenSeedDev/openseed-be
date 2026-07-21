package com.seedrank.member.profile;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateProfileIdRequest(
        @Schema(example = "new_seed_id", pattern = "^[A-Za-z0-9_]{3,20}$",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String profileId) {
}
