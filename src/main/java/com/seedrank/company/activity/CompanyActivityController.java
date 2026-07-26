package com.seedrank.company.activity;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/me/company-activity")
class CompanyActivityController {
    private final CompanyActivityService service;

    CompanyActivityController(CompanyActivityService service) {
        this.service = service;
    }

    @Operation(
            summary = "내 Company 관심과 문의 조회",
            description = "회사 인증을 마친 Company가 자신이 관심 등록한 아이디어와 문의 스레드를 최신순으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company 활동 조회 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "403", description = "회사 인증 필요")
    })
    @GetMapping
    CompanyActivityResponse get(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.get(authorization);
    }
}
