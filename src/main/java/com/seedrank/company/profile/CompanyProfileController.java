package com.seedrank.company.profile;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/companies/profile")
class CompanyProfileController {
    private final CompanyProfileService service;

    CompanyProfileController(CompanyProfileService service) {
        this.service = service;
    }

    @Operation(summary = "회사 프로필 등록")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회사 프로필 등록 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 또는 회사 이메일 도메인 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "409", description = "회사 프로필 또는 이메일 중복")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CompanyProfileResponse create(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CompanyProfileRequest request) {
        return service.create(authorization, request);
    }
}
