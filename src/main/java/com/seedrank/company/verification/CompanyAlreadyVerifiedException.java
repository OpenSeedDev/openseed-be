package com.seedrank.company.verification;

public class CompanyAlreadyVerifiedException extends RuntimeException {
    public CompanyAlreadyVerifiedException() {
        super("이미 인증된 회사 프로필입니다.");
    }
}
