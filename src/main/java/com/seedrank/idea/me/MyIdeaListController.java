package com.seedrank.idea.me;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seedrank.idea.IdeaStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/me")
class MyIdeaListController {
    private final MyIdeaListService service;

    MyIdeaListController(MyIdeaListService service) {
        this.service = service;
    }

    @Operation(summary = "내 아이디어 목록", description = "작성한 아이디어를 최신 수정순으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 상태·Cursor·페이지 크기"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증")
    })
    @GetMapping("/ideas")
    MyIdeaPageResponse get(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(description = "아이디어 상태")
            @RequestParam(required = false) IdeaStatus status,
            @Parameter(description = "이전 응답의 nextCursor")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return service.get(authorization, status, cursor, size);
    }
}
