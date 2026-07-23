package com.seedrank.ranking.main;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RankingMainQuery {
    private final JdbcTemplate jdbc;

    RankingMainQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<RankingCardResponse> findCurrentRanking() {
        return jdbc.query("""
                SELECT ranking.rank_position,
                       ranking.previous_rank_position,
                       idea.title,
                       idea.summary,
                       idea.category,
                       COALESCE((ranking.components ->> 'companyInterestCount')::integer, 0)
                           AS company_interest_count,
                       COALESCE((ranking.components ->> 'likeCount')::integer, 0) AS like_count
                FROM ranking_current ranking
                JOIN ideas idea ON idea.id = ranking.idea_id
                WHERE idea.status = 'PUBLISHED'
                ORDER BY ranking.rank_position
                """, (resultSet, rowNumber) -> {
                    int rank = resultSet.getInt("rank_position");
                    Integer previousRank = resultSet.getObject("previous_rank_position", Integer.class);
                    Integer rankChange = previousRank == null ? null : previousRank - rank;
                    return new RankingCardResponse(
                            rank,
                            rankChange,
                            resultSet.getString("title"),
                            resultSet.getString("summary"),
                            resultSet.getString("category"),
                            resultSet.getInt("company_interest_count"),
                            resultSet.getInt("like_count"));
                });
    }
}
