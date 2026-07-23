package com.seedrank.messaging.message;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "읽음 상태 없는 텍스트 메시지")
record MessageResponse(UUID id, String content, UUID senderId, Instant sentAt) {
    static MessageResponse from(Message message) {
        return new MessageResponse(message.id(), message.content(), message.senderId(), message.sentAt());
    }
}
