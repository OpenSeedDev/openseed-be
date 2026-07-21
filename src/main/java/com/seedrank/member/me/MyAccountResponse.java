package com.seedrank.member.me;

import java.util.UUID;

import com.seedrank.member.User;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증된 사용자의 계정 정보")
public record MyAccountResponse(
        UUID userId,
        String profileId,
        User.Role role,
        CompanyVerificationStatus companyVerificationStatus) {

    static MyAccountResponse from(User user, CompanyVerificationStatus companyVerificationStatus) {
        return new MyAccountResponse(
                user.getId(),
                user.getProfileId(),
                user.getRole(),
                companyVerificationStatus);
    }
}
