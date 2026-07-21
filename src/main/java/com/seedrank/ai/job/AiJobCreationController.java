package com.seedrank.ai.job;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/ai/idea-jobs")
class AiJobCreationController {
    private final AiJobCreationService service;

    AiJobCreationController(AiJobCreationService service) {
        this.service = service;
    }

    @Operation(summary = "AI 아이디어 생성 Job 접수")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Job 접수 또는 기존 Job 반환"),
            @ApiResponse(responseCode = "400", description = "입력값 또는 Idempotency-Key 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key를 다른 입력에 재사용")
    })
    @PostMapping
    ResponseEntity<AiJobCreationResponse> create(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(required = true, description = "사용자별 중복 요청 방지 키(1~100자)")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AiJobCreationRequest request) {
        AiJobCreationResponse response = service.create(authorization, idempotencyKey, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/ai/idea-jobs/" + response.jobId()))
                .body(response);
    }
}
