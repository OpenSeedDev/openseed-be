package com.seedrank.search;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.ranking.main.RankingCardResponse;

@Service
class IdeaSearchService {
    private static final int MAX_QUERY_LENGTH = 20;

    private final IdeaSearchQuery query;

    IdeaSearchService(IdeaSearchQuery query) {
        this.query = query;
    }

    @Transactional(readOnly = true)
    List<RankingCardResponse> search(String rawQuery) {
        String normalized = normalize(rawQuery);
        return query.findCurrentRanking(normalized);
    }

    private String normalize(String rawQuery) {
        String normalized = rawQuery == null ? "" : rawQuery.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || length > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("검색어는 공백이 아닌 20자 이하여야 합니다.");
        }
        return normalized;
    }
}

