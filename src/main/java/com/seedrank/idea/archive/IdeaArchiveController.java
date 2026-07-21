package com.seedrank.idea.archive;

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
@RequestMapping("/api/v1/ideas/{ideaId}/archive")
class IdeaArchiveController {
    private final IdeaArchiveService service;

    IdeaArchiveController(IdeaArchiveService service) {
        this.service = service;
    }

    @Operation(summary = "아이디어 보관", description = "작성자가 게시된 아이디어를 보관해 공개 노출을 중단합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "보관 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "작성자의 아이디어를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "게시 상태가 아닌 아이디어")
    })
    @PostMapping
    IdeaArchiveResponse archive(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        return service.archive(authorization, ideaId);
    }
}
