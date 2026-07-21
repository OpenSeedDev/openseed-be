package com.seedrank.ai.job;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seedrank.idea.draft.IdeaDraftResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai/idea-jobs")
class AiCandidateDraftController {
    private final AiCandidateDraftService service;

    AiCandidateDraftController(AiCandidateDraftService service) {
        this.service = service;
    }

    @Operation(summary = "선택·편집한 AI 후보를 Idea Draft로 저장")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "AI 후보 기반 Draft 생성 성공"),
            @ApiResponse(responseCode = "400", description = "후보 번호 또는 편집 내용 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "소유한 AI Job을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "선택 불가능한 Job 또는 이미 선택된 Job")
    })
    @PostMapping("/{jobId}/draft")
    ResponseEntity<IdeaDraftResponse> create(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID jobId,
            @Valid @RequestBody AiCandidateDraftRequest request) {
        IdeaDraftResponse response = service.create(authorization, jobId, request);
        return ResponseEntity.created(URI.create("/api/v1/ideas/" + response.id())).body(response);
    }
}
