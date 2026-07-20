package com.seedrank.auth.signup;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.member.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
        UUID userId,
        String profileId,
        User.Status status,
        @Schema(example = "300") int pointBalance,
        Instant createdAt) {

    static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getProfileId(),
                user.getStatus(),
                300,
                user.getCreatedAt());
    }
}
