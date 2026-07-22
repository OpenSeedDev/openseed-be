package com.seedrank.feedback.accept;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/feedbacks/{feedbackId}/accept")
class FeedbackAcceptController {
    private final FeedbackAcceptService service;

    FeedbackAcceptController(FeedbackAcceptService service) {
        this.service = service;
    }

    @Operation(summary = "피드백 채택과 기여 보상")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contribution·Point·타임라인 원자 처리 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "채택 가능한 피드백을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 채택된 피드백")
    })
    @PostMapping
    FeedbackAcceptResponse accept(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID feedbackId) {
        return service.accept(authorization, feedbackId);
    }
}
