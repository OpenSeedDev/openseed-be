package com.seedrank.idea.question;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ideas/{ideaId}/validation-questions")
class ValidationQuestionController {
    private final ValidationQuestionService service;

    ValidationQuestionController(ValidationQuestionService service) {
        this.service = service;
    }

    @Operation(summary = "아이디어 검증 질문 전체 교체")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 질문 저장 성공"),
            @ApiResponse(responseCode = "400", description = "질문 개수 또는 입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "작성자의 아이디어를 찾을 수 없음")
    })
    @PutMapping
    ValidationQuestionsResponse replace(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId,
            @Valid @RequestBody ValidationQuestionRequest request) {
        return service.replace(authorization, ideaId, request);
    }
}
