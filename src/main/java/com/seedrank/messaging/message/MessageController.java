package com.seedrank.messaging.message;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/message-threads/{threadId}/messages")
class MessageController {
    private final MessageService service;

    MessageController(MessageService service) {
        this.service = service;
    }

    @Operation(summary = "문의 텍스트 메시지 발송", description = "Thread 참여자만 읽음 상태 없는 텍스트 메시지를 보냅니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "메시지 발송 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 텍스트"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "참여 가능한 스레드를 찾을 수 없음")
    })
    @PostMapping
    ResponseEntity<MessageResponse> send(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID threadId,
            @Valid @RequestBody MessageRequest request) {
        MessageResponse response = service.send(authorization, threadId, request.content());
        return ResponseEntity.created(URI.create(
                "/api/v1/message-threads/" + threadId + "/messages/" + response.id())).body(response);
    }

    @Operation(summary = "문의 텍스트 메시지 목록", description = "Thread 참여자가 메시지를 발신 시각순 Cursor로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 Cursor 또는 페이지 크기"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증"),
            @ApiResponse(responseCode = "404", description = "참여 가능한 스레드를 찾을 수 없음")
    })
    @GetMapping
    MessagePageResponse list(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID threadId,
            @Parameter(description = "이전 응답의 nextCursor")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return service.list(authorization, threadId, cursor, size);
    }
}
