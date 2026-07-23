package com.seedrank.ranking.publish;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.seedrank.ranking.calculate.RankingSignal;

@Repository
class RankingSignalQuery {
    private final JdbcTemplate jdbc;

    RankingSignalQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<RankingSignal> snapshot(Instant targetHour) {
        Timestamp from = Timestamp.from(targetHour.minusSeconds(24 * 60 * 60));
        Timestamp until = Timestamp.from(targetHour);
        return jdbc.query("""
                WITH lot_metrics AS (
                    SELECT idea_id,
                           COALESCE(SUM(principal), 0) AS active_principal,
                           COUNT(DISTINCT user_id) AS unique_investors
                    FROM seed_unit_lots
                    WHERE status = 'LOCKED'
                    GROUP BY idea_id
                ),
                feedback_metrics AS (
                    SELECT idea_id,
                           COUNT(*) AS feedback_count,
                           COUNT(*) FILTER (WHERE accepted_at IS NOT NULL) AS accepted_count
                    FROM feedbacks
                    WHERE deleted_at IS NULL
                    GROUP BY idea_id
                ),
                like_metrics AS (
                    SELECT idea_id, COUNT(*) AS like_count
                    FROM idea_likes
                    GROUP BY idea_id
                ),
                company_interest_metrics AS (
                    SELECT idea_id, COUNT(*) AS company_interest_count
                    FROM company_interests
                    GROUP BY idea_id
                ),
                growth_metrics AS (
                    SELECT idea_id, COALESCE(SUM(view_delta), 0) AS recent_views
                    FROM idea_metric_hourly
                    WHERE bucket_hour >= ? AND bucket_hour < ?
                    GROUP BY idea_id
                ),
                maximum_growth AS (
                    SELECT COALESCE(MAX(recent_views), 0) AS max_recent_views
                    FROM growth_metrics
                )
                SELECT idea.id AS idea_id,
                       idea.published_at,
                       COALESCE(lot.active_principal, 0) AS active_principal,
                       COALESCE(lot.unique_investors, 0) AS unique_investors,
                       COALESCE(feedback.feedback_count, 0) AS feedback_count,
                       COALESCE(feedback.accepted_count, 0) AS accepted_count,
                       COALESCE(company_interest.company_interest_count, 0) AS company_interest_count,
                       COALESCE(likes.like_count, 0) AS like_count,
                       COALESCE(metric.view_count, 0) AS view_count,
                       CASE WHEN maximum.max_recent_views = 0 THEN 0.0
                            ELSE COALESCE(growth.recent_views, 0) * 15.0 / maximum.max_recent_views
                       END AS normalized_growth
                FROM ideas idea
                LEFT JOIN lot_metrics lot ON lot.idea_id = idea.id
                LEFT JOIN feedback_metrics feedback ON feedback.idea_id = idea.id
                LEFT JOIN company_interest_metrics company_interest ON company_interest.idea_id = idea.id
                LEFT JOIN like_metrics likes ON likes.idea_id = idea.id
                LEFT JOIN idea_metric_current metric ON metric.idea_id = idea.id
                LEFT JOIN growth_metrics growth ON growth.idea_id = idea.id
                CROSS JOIN maximum_growth maximum
                WHERE idea.status = 'PUBLISHED' AND idea.published_at <= ?
                ORDER BY idea.id
                """, (resultSet, rowNumber) -> new RankingSignal(
                        resultSet.getObject("idea_id", java.util.UUID.class),
                        resultSet.getLong("active_principal"),
                        resultSet.getInt("unique_investors"),
                        resultSet.getInt("company_interest_count"),
                        resultSet.getInt("feedback_count"),
                        resultSet.getInt("accepted_count"),
                        resultSet.getInt("like_count"),
                        resultSet.getLong("view_count"),
                        resultSet.getDouble("normalized_growth"),
                        resultSet.getTimestamp("published_at").toInstant()),
                from, until, until);
    }
}
