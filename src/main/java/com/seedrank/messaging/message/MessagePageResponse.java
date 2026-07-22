package com.seedrank.messaging.message;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "텍스트 메시지 Cursor 목록")
record MessagePageResponse(List<MessageResponse> items, String nextCursor, boolean hasNext) {
}
