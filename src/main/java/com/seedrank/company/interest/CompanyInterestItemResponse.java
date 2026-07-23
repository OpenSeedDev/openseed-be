package com.seedrank.company.interest;

import java.time.Instant;

record CompanyInterestItemResponse(String companyName, Instant interestedAt) {
    static CompanyInterestItemResponse from(CompanyInterest interest) {
        return new CompanyInterestItemResponse(interest.companyName(), interest.interestedAt());
    }
}
