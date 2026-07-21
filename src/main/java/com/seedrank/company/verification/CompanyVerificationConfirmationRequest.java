package com.seedrank.company.verification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회사 이메일 인증 완료 요청")
record CompanyVerificationConfirmationRequest(
        @NotBlank(message = "인증 토큰을 입력해 주세요.")
        @Schema(description = "인증 메일의 일회성 토큰")
        String token) {
}
