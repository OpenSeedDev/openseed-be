package com.seedrank.company.verification;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회사 인증 메일 발송 접수 결과. 회사 이메일과 인증 토큰은 반환하지 않는다.")
record CompanyVerificationResponse(
        @Schema(description = "인증 링크 만료 시각") Instant expiresAt) {
}
