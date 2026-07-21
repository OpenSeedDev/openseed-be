package com.seedrank.company.verification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/companies/verifications/confirm")
class CompanyVerificationConfirmationController {
    private final CompanyVerificationConfirmationService service;

    CompanyVerificationConfirmationController(CompanyVerificationConfirmationService service) {
        this.service = service;
    }

    @Operation(summary = "회사 이메일 인증 완료")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "회사 인증과 Company 권한 부여 완료"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 회사 인증 토큰")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@Valid @RequestBody CompanyVerificationConfirmationRequest request) {
        service.confirm(request.token());
    }
}
