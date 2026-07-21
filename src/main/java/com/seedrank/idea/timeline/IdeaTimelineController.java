package com.seedrank.idea.timeline;

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

@RestController
@RequestMapping("/api/v1/ideas/{ideaId}/timeline")
class IdeaTimelineController {
    private final IdeaTimelineService service;

    IdeaTimelineController(IdeaTimelineService service) {
        this.service = service;
    }

    @Operation(summary = "아이디어 성장 타임라인", description = "게시·수정 등 아이디어 성장 이벤트를 발생 시각순으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "타임라인 조회 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 헤더"),
            @ApiResponse(responseCode = "404", description = "조회 가능한 아이디어를 찾을 수 없음")
    })
    @GetMapping
    IdeaTimelineResponse get(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        return service.get(authorization, ideaId);
    }
}
