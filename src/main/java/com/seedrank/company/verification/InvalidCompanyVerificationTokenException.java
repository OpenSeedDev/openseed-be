package com.seedrank.company.verification;

public class InvalidCompanyVerificationTokenException extends RuntimeException {
    public InvalidCompanyVerificationTokenException() {
        super("회사 인증 링크가 유효하지 않습니다.");
    }
}
