package com.seedrank.company.interest;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/ideas/{ideaId}")
class CompanyInterestController {
    private final CompanyInterestService service;

    CompanyInterestController(CompanyInterestService service) {
        this.service = service;
    }

    @Operation(summary = "기업 관심 등록", description = "회사 인증을 마친 Company가 공개 범위와 관계없이 게시된 아이디어에 관심을 멱등 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 또는 이미 등록된 상태 반환"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "403", description = "회사 인증 필요"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어를 찾을 수 없음")
    })
    @PutMapping("/company-interest")
    CompanyInterestResponse register(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        return service.register(authorization, ideaId);
    }

    @Operation(summary = "기업 관심 취소", description = "이미 취소된 요청도 성공으로 처리합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 또는 이미 취소된 상태 반환"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "403", description = "회사 인증 필요"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어를 찾을 수 없음")
    })
    @DeleteMapping("/company-interest")
    CompanyInterestResponse remove(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        return service.remove(authorization, ideaId);
    }

    @Operation(summary = "관심 기업 전체 공개 목록", description = "Guest를 포함한 모든 사용자에게 회사명과 관심 등록 시각만 공개합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관심 기업 공개 목록"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어를 찾을 수 없음")
    })
    @GetMapping("/company-interests")
    CompanyInterestListResponse list(@PathVariable UUID ideaId) {
        return service.list(ideaId);
    }
}
