package com.seedrank.company.interest;

public class CompanyInterestIdeaNotFoundException extends RuntimeException {
    public CompanyInterestIdeaNotFoundException() {
        super("게시된 아이디어를 찾을 수 없습니다.");
    }
}
