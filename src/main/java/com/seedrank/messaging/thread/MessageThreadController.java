package com.seedrank.messaging.thread;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/ideas/{ideaId}/message-thread")
class MessageThreadController {
    private final MessageThreadService service;

    MessageThreadController(MessageThreadService service) {
        this.service = service;
    }

    @Operation(summary = "기업 문의 스레드 생성")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "스레드 생성"),
            @ApiResponse(responseCode = "200", description = "기존 스레드 반환"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "403", description = "인증 Company 권한 필요"),
            @ApiResponse(responseCode = "404", description = "게시 아이디어를 찾을 수 없음")
    })
    @PostMapping
    ResponseEntity<MessageThreadResponse> create(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID ideaId) {
        var result = service.create(authorization, ideaId);
        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.created(URI.create("/api/v1/message-threads/" + result.response().id()))
                .body(result.response());
    }
}
