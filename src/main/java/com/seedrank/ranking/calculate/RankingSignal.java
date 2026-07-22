package com.seedrank.ranking.calculate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RankingSignal(
        UUID ideaId,
        long activePrincipal,
        int uniqueActiveInvestors,
        int companyInterestCount,
        int feedbackCount,
        int acceptedFeedbackCount,
        int likeCount,
        long viewCount,
        double normalized24hGrowth,
        Instant publishedAt) {

    public RankingSignal {
        Objects.requireNonNull(ideaId, "ideaId must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        requireNonNegative(activePrincipal, "activePrincipal");
        requireNonNegative(uniqueActiveInvestors, "uniqueActiveInvestors");
        requireNonNegative(companyInterestCount, "companyInterestCount");
        requireNonNegative(feedbackCount, "feedbackCount");
        requireNonNegative(acceptedFeedbackCount, "acceptedFeedbackCount");
        requireNonNegative(likeCount, "likeCount");
        requireNonNegative(viewCount, "viewCount");
        if (acceptedFeedbackCount > feedbackCount) {
            throw new IllegalArgumentException("acceptedFeedbackCount must not exceed feedbackCount");
        }
        if (!Double.isFinite(normalized24hGrowth) || normalized24hGrowth < 0) {
            throw new IllegalArgumentException("normalized24hGrowth must be finite and non-negative");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
