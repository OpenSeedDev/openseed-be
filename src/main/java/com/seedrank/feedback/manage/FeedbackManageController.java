package com.seedrank.feedback.manage;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
@RequestMapping("/api/v1/feedbacks/{feedbackId}")
class FeedbackManageController {
    private final FeedbackManageService service;

    FeedbackManageController(FeedbackManageService service) {
        this.service = service;
    }

    @Operation(summary = "내 피드백 수정")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정과 이전 스냅샷 저장 성공"),
            @ApiResponse(responseCode = "400", description = "유형·본문·근거 입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "수정 가능한 피드백을 찾을 수 없음")
    })
    @PutMapping
    FeedbackUpdateResponse update(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID feedbackId,
            @Valid @RequestBody FeedbackUpdateRequest request) {
        return service.update(authorization, feedbackId, request);
    }

    @Operation(summary = "내 피드백 삭제")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "soft delete와 이전 스냅샷 저장 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "삭제 가능한 피드백을 찾을 수 없음")
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID feedbackId) {
        service.delete(authorization, feedbackId);
    }
}
