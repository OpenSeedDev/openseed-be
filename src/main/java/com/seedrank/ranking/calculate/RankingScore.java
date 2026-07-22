package com.seedrank.ranking.calculate;

public record RankingScore(
        double investmentScore,
        double diversityScore,
        double companyScore,
        double feedbackScore,
        double reactionScore,
        double growthScore,
        double decayScore,
        double subtotalScore,
        double totalScore) {
}
