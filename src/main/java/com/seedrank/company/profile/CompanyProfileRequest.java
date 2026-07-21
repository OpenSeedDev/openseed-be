package com.seedrank.company.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회사 프로필 등록 요청")
public record CompanyProfileRequest(
        @NotBlank @Size(max = 100) String companyName,
        @NotBlank @Size(max = 254) String companyEmail) {
}
