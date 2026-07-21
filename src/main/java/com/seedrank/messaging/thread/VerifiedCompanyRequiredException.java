package com.seedrank.messaging.thread;

public class VerifiedCompanyRequiredException extends RuntimeException {
    public VerifiedCompanyRequiredException() {
        super("회사 이메일 인증을 완료한 Company만 문의할 수 있습니다.");
    }
}
