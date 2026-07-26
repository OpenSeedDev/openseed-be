package com.seedrank.company.activity;

import java.util.List;

public record CompanyActivityResponse(
        List<CompanyInterestActivityResponse> interests,
        List<CompanyInquiryActivityResponse> inquiries) {
}
