package com.seedrank.company.verification;

public class CompanyProfileRequiredException extends RuntimeException {
    public CompanyProfileRequiredException() {
        super("회사 프로필 등록이 필요합니다.");
    }
}
