package com.seedrank.member.profile;

public class ProfileIdValidationException extends RuntimeException {

    public ProfileIdValidationException() {
        super("사용할 수 없는 프로필 아이디입니다.");
    }
}
