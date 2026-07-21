package com.seedrank.company.profile;

public class CompanyProfileValidationException extends RuntimeException {
    private final String field;

    private CompanyProfileValidationException(String message, String field) {
        super(message);
        this.field = field;
    }

    static CompanyProfileValidationException invalidEmail() {
        return new CompanyProfileValidationException("유효한 회사 이메일을 입력해 주세요.", "companyEmail");
    }

    public String getField() {
        return field;
    }
}
