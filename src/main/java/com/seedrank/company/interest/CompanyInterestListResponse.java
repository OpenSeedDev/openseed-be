package com.seedrank.company.interest;

import java.util.List;

record CompanyInterestListResponse(List<CompanyInterestItemResponse> items, long interestCount) {
}
