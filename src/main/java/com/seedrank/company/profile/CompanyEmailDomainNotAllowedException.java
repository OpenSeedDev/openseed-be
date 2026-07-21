package com.seedrank.company.profile;

public class CompanyEmailDomainNotAllowedException extends RuntimeException {
    public CompanyEmailDomainNotAllowedException() {
        super("무료 개인 메일 도메인은 회사 인증에 사용할 수 없습니다.");
    }
}
