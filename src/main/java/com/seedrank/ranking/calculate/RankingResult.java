package com.seedrank.ranking.calculate;

import java.time.Instant;
import java.util.UUID;

public record RankingResult(
        UUID ideaId,
        int position,
        RankingScore score,
        int uniqueActiveInvestors,
        int acceptedFeedbackCount,
        int companyInterestCount,
        int likeCount,
        Instant publishedAt,
        Instant calculatedAt) {
}
