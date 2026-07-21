package com.seedrank.ai.job;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/ai/idea-jobs")
class AiJobResultQueryController {
    private final AiJobResultQueryService service;

    AiJobResultQueryController(AiJobResultQueryService service) {
        this.service = service;
    }

    @Operation(summary = "내 AI 아이디어 생성 Job 상태와 결과 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job 상태와 결과 또는 실패 코드 조회 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "소유한 Job을 찾을 수 없음")
    })
    @GetMapping("/{jobId}")
    AiJobResultResponse get(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID jobId) {
        return service.get(authorization, jobId);
    }
}
