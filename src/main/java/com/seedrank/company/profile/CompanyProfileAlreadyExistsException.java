package com.seedrank.company.profile;

public class CompanyProfileAlreadyExistsException extends RuntimeException {
    public CompanyProfileAlreadyExistsException() {
        super("이미 등록된 회사 프로필 또는 회사 이메일입니다.");
    }
}
