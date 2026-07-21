package com.seedrank.company.verification;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/companies/verifications")
class CompanyVerificationController {
    private final CompanyVerificationService service;

    CompanyVerificationController(CompanyVerificationService service) {
        this.service = service;
    }

    @Operation(summary = "회사 인증 메일 발송")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "인증 메일 발송 접수"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "409", description = "회사 프로필 없음 또는 이미 인증 완료")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    CompanyVerificationResponse send(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.send(authorization);
    }
}
