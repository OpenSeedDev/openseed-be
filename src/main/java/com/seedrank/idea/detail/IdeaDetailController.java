package com.seedrank.idea.detail;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/ideas")
class IdeaDetailController {
    private final IdeaDetailService service;

    IdeaDetailController(IdeaDetailService service) {
        this.service = service;
    }

    @Operation(
            summary = "공개 범위별 아이디어 상세 조회",
            description = "Authorization을 생략하면 Guest로 조회하며 공개 범위에 따라 비공개 필드를 응답에서 제외합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = IdeaDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "제공한 인증이 유효하지 않음"),
            @ApiResponse(responseCode = "404", description = "조회 가능한 아이디어를 찾을 수 없음")
    })
    @GetMapping("/{ideaId}")
    IdeaDetailResponse get(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        return service.get(authorization, ideaId);
    }
}
