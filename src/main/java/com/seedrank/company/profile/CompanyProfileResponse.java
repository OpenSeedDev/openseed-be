package com.seedrank.company.profile;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.member.me.CompanyVerificationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "등록된 회사 프로필. 회사 이메일은 반환하지 않는다.")
public record CompanyProfileResponse(
        UUID companyProfileId,
        String companyName,
        String companyDomain,
        CompanyVerificationStatus verificationStatus,
        Instant createdAt) {

    static CompanyProfileResponse from(CompanyProfile profile) {
        return new CompanyProfileResponse(
                profile.getId(),
                profile.getCompanyName(),
                profile.getCompanyDomain(),
                CompanyVerificationStatus.PENDING,
                profile.getCreatedAt());
    }
}
