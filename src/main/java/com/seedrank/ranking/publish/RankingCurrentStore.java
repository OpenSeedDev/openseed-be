package com.seedrank.ranking.publish;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.ranking.calculate.RankingResult;
import com.seedrank.ranking.calculate.RankingScore;

@Repository
public class RankingCurrentStore {
    private final JdbcTemplate jdbc;

    RankingCurrentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public RankingPublicationOutcome replace(Instant targetHour, List<RankingResult> results) {
        validateHour(targetHour);
        if (!claim(targetHour)) {
            return new RankingPublicationOutcome(false, targetHour, currentCount());
        }
        publishClaimed(targetHour, results);
        return new RankingPublicationOutcome(true, targetHour, results.size());
    }

    boolean claim(Instant targetHour) {
        validateHour(targetHour);
        jdbc.queryForObject("SELECT id FROM ranking_publish_lock WHERE id=1 FOR UPDATE", Short.class);
        Instant latest = jdbc.queryForObject("SELECT max(target_hour) FROM ranking_runs", Instant.class);
        if (latest != null && !targetHour.isAfter(latest)) {
            return false;
        }
        jdbc.update("INSERT INTO ranking_runs(target_hour, idea_count, published_at) VALUES (?, 0, CURRENT_TIMESTAMP)",
                Timestamp.from(targetHour));
        return true;
    }

    void publishClaimed(Instant targetHour, List<RankingResult> results) {
        Map<UUID, Integer> previousPositions = new HashMap<>();
        jdbc.query("SELECT idea_id, rank_position FROM ranking_current", resultSet -> {
            previousPositions.put(resultSet.getObject("idea_id", UUID.class), resultSet.getInt("rank_position"));
        });
        jdbc.update("DELETE FROM ranking_current");
        for (RankingResult result : results) {
            if (!targetHour.equals(result.calculatedAt())) {
                throw new IllegalArgumentException("Every result must belong to targetHour");
            }
            RankingScore score = result.score();
            jdbc.update("""
                    INSERT INTO ranking_current(
                        idea_id, rank_position, previous_rank_position, total_score, components, calculated_at)
                    VALUES (?, ?, ?, ?, jsonb_build_object(
                        'investment', ?::double precision,
                        'diversity', ?::double precision,
                        'company', ?::double precision,
                        'feedback', ?::double precision,
                        'reaction', ?::double precision,
                        'growth', ?::double precision,
                        'decay', ?::double precision,
                        'subtotal', ?::double precision,
                        'companyInterestCount', ?::integer,
                        'likeCount', ?::integer
                    ), ?)
                    """, result.ideaId(), result.position(), previousPositions.get(result.ideaId()), score.totalScore(),
                    score.investmentScore(), score.diversityScore(), score.companyScore(), score.feedbackScore(),
                    score.reactionScore(), score.growthScore(), score.decayScore(), score.subtotalScore(),
                    result.companyInterestCount(), result.likeCount(),
                    Timestamp.from(targetHour));
        }
        jdbc.update("UPDATE ranking_runs SET idea_count=?, published_at=CURRENT_TIMESTAMP WHERE target_hour=?",
                results.size(), Timestamp.from(targetHour));
    }

    int currentCount() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM ranking_current", Integer.class);
        return count == null ? 0 : count;
    }

    private static void validateHour(Instant targetHour) {
        if (targetHour == null || !targetHour.equals(targetHour.truncatedTo(ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("targetHour must be aligned to a UTC hour");
        }
    }
}
