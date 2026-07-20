package com.seedrank.auth.signup;

public class SignupValidationException extends RuntimeException {

    private final String code;
    private final String field;

    private SignupValidationException(String code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    static SignupValidationException invalidEmail() {
        return new SignupValidationException("VALIDATION_ERROR", "올바른 이메일을 입력해 주세요.", "email");
    }

    static SignupValidationException invalidPassword() {
        return new SignupValidationException("VALIDATION_ERROR", "비밀번호는 8자 이상, UTF-8 기준 72바이트 이하여야 합니다.", "password");
    }

    static SignupValidationException invalidProfileId() {
        return new SignupValidationException("INVALID_PROFILE_ID", "사용할 수 없는 프로필 아이디입니다.", "profileId");
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
