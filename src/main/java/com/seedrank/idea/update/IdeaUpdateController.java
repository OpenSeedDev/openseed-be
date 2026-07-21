package com.seedrank.idea.update;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/ideas/{ideaId}")
class IdeaUpdateController {
    private final IdeaUpdateService service;

    IdeaUpdateController(IdeaUpdateService service) {
        this.service = service;
    }

    @Operation(
            summary = "게시된 아이디어 내용 수정",
            description = "작성자만 최신 내용을 수정합니다. 내부 전체 버전 스냅샷은 저장하지만 조회 API는 제공하지 않습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "작성자의 아이디어를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "게시되지 않은 아이디어")
    })
    @PatchMapping
    IdeaUpdateResponse update(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId,
            @Valid @RequestBody IdeaUpdateRequest request) {
        return service.update(authorization, ideaId, request);
    }
}
