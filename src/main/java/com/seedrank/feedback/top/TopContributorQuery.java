package com.seedrank.feedback.top;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class TopContributorQuery {
    private final JdbcTemplate jdbc;

    TopContributorQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<TopContributorResponse> findTopFive(Instant recentThreshold) {
        return jdbc.query("""
                WITH contributor_stats AS (
                    SELECT contribution.user_id,
                           COUNT(*) AS total_count,
                           COUNT(*) FILTER (WHERE contribution.created_at >= ?) AS recent_count,
                           MAX(contribution.created_at) AS last_contributed_at
                    FROM contributions contribution
                    GROUP BY contribution.user_id
                ),
                top_contributors AS (
                    SELECT *
                    FROM contributor_stats
                    ORDER BY total_count DESC,
                             recent_count DESC,
                             last_contributed_at DESC,
                             user_id ASC
                    LIMIT 5
                ),
                category_ranks AS (
                    SELECT contribution.user_id,
                           idea.category,
                           ROW_NUMBER() OVER (
                               PARTITION BY contribution.user_id
                               ORDER BY COUNT(*) DESC, idea.category ASC
                           ) AS category_rank
                    FROM contributions contribution
                    JOIN ideas idea ON idea.id = contribution.idea_id
                    JOIN top_contributors top ON top.user_id = contribution.user_id
                    GROUP BY contribution.user_id, idea.category
                )
                SELECT user_account.profile_id,
                       top.total_count,
                       top.recent_count,
                       category.category AS primary_category
                FROM top_contributors top
                JOIN users user_account ON user_account.id = top.user_id
                JOIN category_ranks category
                  ON category.user_id = top.user_id
                 AND category.category_rank = 1
                ORDER BY top.total_count DESC,
                         top.recent_count DESC,
                         top.last_contributed_at DESC,
                         top.user_id ASC
                """,
                (resultSet, rowNumber) -> new TopContributorResponse(
                        resultSet.getString("profile_id"),
                        resultSet.getLong("total_count"),
                        resultSet.getLong("recent_count"),
                        resultSet.getString("primary_category")),
                Timestamp.from(recentThreshold));
    }
}
