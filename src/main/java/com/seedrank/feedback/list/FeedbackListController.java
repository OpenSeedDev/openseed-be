package com.seedrank.feedback.list;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/v1/ideas/{ideaId}/feedbacks")
class FeedbackListController {
    private final FeedbackListService service;

    FeedbackListController(FeedbackListService service) {
        this.service = service;
    }

    @Operation(summary = "피드백 목록", description = "삭제되지 않은 피드백을 채택 우선, 최신 작성순 Cursor로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 Cursor 또는 페이지 크기"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어를 찾을 수 없음")
    })
    @GetMapping
    FeedbackPageResponse get(
            @PathVariable UUID ideaId,
            @Parameter(description = "이전 응답의 nextCursor")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return service.get(ideaId, cursor, size);
    }
}
