package com.seedrank.idea.publish;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/v1/ideas/{ideaId}/publish")
class IdeaPublishController {
    private final IdeaPublishService service;

    IdeaPublishController(IdeaPublishService service) {
        this.service = service;
    }

    @Operation(summary = "아이디어 게시")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시 성공"),
            @ApiResponse(responseCode = "400", description = "게시 조건 또는 입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "작성자의 아이디어를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 게시된 아이디어")
    })
    @PostMapping
    IdeaPublishResponse publish(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId,
            @Valid @RequestBody IdeaPublishRequest request) {
        return service.publish(authorization, ideaId, request);
    }
}
