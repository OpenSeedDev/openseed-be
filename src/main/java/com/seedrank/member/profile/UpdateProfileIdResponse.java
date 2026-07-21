package com.seedrank.member.profile;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.member.User;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "수정된 공개 프로필 정보")
public record UpdateProfileIdResponse(
        UUID userId,
        String profileId,
        Instant updatedAt) {

    static UpdateProfileIdResponse from(User user) {
        return new UpdateProfileIdResponse(user.getId(), user.getProfileId(), user.getUpdatedAt());
    }
}
