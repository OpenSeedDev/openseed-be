package com.seedrank.messaging.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "텍스트 메시지 발송 요청")
record MessageRequest(
        @NotBlank(message = "메시지 내용을 입력해 주세요.")
        @Size(max = Message.MAX_CONTENT_LENGTH, message = "메시지는 2000자 이하여야 합니다.")
        @Schema(description = "앞뒤 공백 제거 후 1~2000자 텍스트", maxLength = 2000)
        String content) {
}
