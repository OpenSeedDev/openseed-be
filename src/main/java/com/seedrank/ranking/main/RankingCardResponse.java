package com.seedrank.ranking.main;

public record RankingCardResponse(
        int rank,
        Integer rankChange,
        String title,
        String summary,
        String category,
        int companyInterestCount,
        int likeCount) {
}
