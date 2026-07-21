package com.seedrank.idea.like;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/ideas/{ideaId}/like")
class IdeaLikeController {
    private final IdeaLikeService service;

    IdeaLikeController(IdeaLikeService service) {
        this.service = service;
    }

    @Operation(summary = "아이디어 좋아요 등록", description = "반복·동시 요청에도 사용자당 하나만 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 또는 이미 등록된 상태 반환"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어를 찾을 수 없음")
    })
    @PutMapping
    IdeaLikeResponse like(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        return service.like(authorization, ideaId);
    }

    @Operation(summary = "아이디어 좋아요 취소", description = "이미 취소된 요청도 성공으로 처리합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 또는 이미 취소된 상태 반환"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "게시된 아이디어를 찾을 수 없음")
    })
    @DeleteMapping
    IdeaLikeResponse unlike(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        return service.unlike(authorization, ideaId);
    }
}
