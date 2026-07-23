package com.seedrank.ranking.main;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RankingMainService {
    private final RankingMainQuery query;

    RankingMainService(RankingMainQuery query) {
        this.query = query;
    }

    @Transactional(readOnly = true)
    List<RankingCardResponse> get() {
        return query.findCurrentRanking();
    }
}
