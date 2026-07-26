package com.seedrank.feedback.top;

public record TopContributorResponse(
        String profileId,
        long totalContributionCount,
        long recent30DayContributionCount,
        String primaryCategory) {
}
