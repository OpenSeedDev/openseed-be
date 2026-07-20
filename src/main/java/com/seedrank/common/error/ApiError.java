package com.seedrank.common.error;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(
        @Schema(example = "EMAIL_ALREADY_EXISTS") String code,
        @Schema(example = "이미 가입된 이메일입니다.") String message,
        String requestId,
        List<ApiFieldError> fieldErrors) {

    static ApiError of(String code, String message, String requestId, List<ApiFieldError> fieldErrors) {
        return new ApiError(code, message, requestId, List.copyOf(fieldErrors));
    }
}
