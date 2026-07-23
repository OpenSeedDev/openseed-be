package com.seedrank.ranking.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.seedrank.TestcontainersConfiguration;
import com.seedrank.ranking.calculate.RankingResult;
import com.seedrank.ranking.calculate.RankingScore;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HourlyRankingPublicationIntegrationTest {

    private static final Instant FIRST_HOUR = Instant.parse("2026-07-22T06:00:00Z");

    @Autowired HourlyRankingPublicationService service;
    @Autowired RankingCurrentStore currentStore;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        cleanRankingAndFixtures();
    }

    @AfterEach
    void cleanup() {
        cleanRankingAndFixtures();
    }

    @Test
    void aggregatesPublishedIdeaSignalsAndPublishesACompleteRanking() {
        UUID richIdea = publishedIdea("rich");
        UUID quietIdea = publishedIdea("quiet");
        UUID archivedIdea = publishedIdea("archived");
        jdbc.update("UPDATE ideas SET status='ARCHIVED' WHERE id=?", archivedIdea);

        UUID firstInvestor = user("investor1");
        UUID secondInvestor = user("investor2");
        lockedLot(richIdea, firstInvestor, 100, FIRST_HOUR.minusSeconds(86_400));
        lockedLot(richIdea, secondInvestor, 50, FIRST_HOUR.minusSeconds(86_400));
        feedback(richIdea, firstInvestor, true);
        feedback(richIdea, secondInvestor, false);
        like(richIdea, firstInvestor);
        companyInterest(richIdea, "company1");
        companyInterest(richIdea, "company2");
        views(richIdea, 40, 12, FIRST_HOUR.minusSeconds(3_600));
        views(quietIdea, 2, 1, FIRST_HOUR.minusSeconds(3_600));

        RankingPublicationOutcome outcome = service.publish(FIRST_HOUR);

        assertThat(outcome).isEqualTo(new RankingPublicationOutcome(true, FIRST_HOUR, 2));
        assertThat(currentIdeaIdsInOrder()).containsExactly(richIdea, quietIdea);
        assertThat(jdbc.queryForObject(
                "SELECT (components ->> 'growth')::double precision FROM ranking_current WHERE idea_id=?",
                Double.class, richIdea)).isEqualTo(15.0);
        assertThat(jdbc.queryForObject(
                "SELECT (components ->> 'companyInterestCount')::integer FROM ranking_current WHERE idea_id=?",
                Integer.class, richIdea)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT (components ->> 'likeCount')::integer FROM ranking_current WHERE idea_id=?",
                Integer.class, richIdea)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT (components ->> 'growth')::double precision FROM ranking_current WHERE idea_id=?",
                Double.class, quietIdea)).isEqualTo(1.25);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking_runs WHERE target_hour=?",
                Integer.class, Timestamp.from(FIRST_HOUR))).isEqualTo(1);
    }

    @Test
    void sequentialAndConcurrentDuplicateRunsPublishTheHourOnlyOnce() throws Exception {
        UUID ideaId = publishedIdea("duplicate");
        assertThat(service.publish(FIRST_HOUR).published()).isTrue();
        double firstScore = totalScore(ideaId);

        views(ideaId, 100, 100, FIRST_HOUR.minusSeconds(60));
        assertThat(service.publish(FIRST_HOUR).published()).isFalse();
        assertThat(totalScore(ideaId)).isEqualTo(firstScore);

        Instant nextHour = FIRST_HOUR.plusSeconds(3_600);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = List.of(
                    executor.submit(() -> publishAfter(start, nextHour)),
                    executor.submit(() -> publishAfter(start, nextHour)),
                    executor.submit(() -> publishAfter(start, nextHour)),
                    executor.submit(() -> publishAfter(start, nextHour)));
            start.countDown();
            assertThat(futures.stream().map(future -> get(future).published()).filter(Boolean::booleanValue).count())
                    .isEqualTo(1);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking_runs WHERE target_hour=?",
                Integer.class, Timestamp.from(nextHour))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking_current", Integer.class)).isEqualTo(1);
    }

    @Test
    void keepsPreviousPositionAndRemovesIdeasNoLongerPublished() {
        UUID first = publishedIdea("previous_first");
        UUID second = publishedIdea("previous_second");
        UUID investor = user("previous_investor");
        lockedLot(first, investor, 100, FIRST_HOUR.minusSeconds(86_400));
        service.publish(FIRST_HOUR);

        jdbc.update("UPDATE ideas SET status='ARCHIVED' WHERE id=?", first);
        RankingPublicationOutcome outcome = service.publish(FIRST_HOUR.plusSeconds(3_600));

        assertThat(outcome.published()).isTrue();
        assertThat(currentIdeaIdsInOrder()).containsExactly(second);
        assertThat(jdbc.queryForObject(
                "SELECT previous_rank_position FROM ranking_current WHERE idea_id=?", Integer.class, second))
                .isEqualTo(2);
    }

    @Test
    void storesRawCompanyInterestAndLikeCountsForRankingCards() {
        UUID ideaId = publishedIdea("card_counts");
        RankingScore score = new RankingScore(1, 2, 3, 4, 5, 6, 0, 21, 21);
        RankingResult result = new RankingResult(
                ideaId, 1, score, 2, 1, 4, 6,
                FIRST_HOUR.minusSeconds(86_400), FIRST_HOUR);

        assertThat(currentStore.replace(FIRST_HOUR, List.of(result)).published()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT (components ->> 'companyInterestCount')::integer FROM ranking_current WHERE idea_id=?",
                Integer.class, ideaId)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT (components ->> 'likeCount')::integer FROM ranking_current WHERE idea_id=?",
                Integer.class, ideaId)).isEqualTo(6);
    }

    @Test
    void failedReplacementRollsBackCurrentRankingAndRunClaim() {
        UUID existing = publishedIdea("rollback_existing");
        service.publish(FIRST_HOUR);
        double existingScore = totalScore(existing);
        Instant failedHour = FIRST_HOUR.plusSeconds(3_600);

        RankingScore score = new RankingScore(1, 0, 0, 0, 0, 0, 0, 1, 1);
        List<RankingResult> invalid = List.of(
                result(UUID.randomUUID(), 1, score, failedHour),
                result(UUID.randomUUID(), 1, score, failedHour));

        assertThatThrownBy(() -> currentStore.replace(failedHour, invalid))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(currentIdeaIdsInOrder()).containsExactly(existing);
        assertThat(totalScore(existing)).isEqualTo(existingScore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking_runs WHERE target_hour=?",
                Integer.class, Timestamp.from(failedHour))).isZero();
    }

    @Test
    void rejectsUnalignedAndOlderHoursWithoutReplacingNewerRanking() {
        UUID ideaId = publishedIdea("time_guard");

        assertThatThrownBy(() -> service.publish(FIRST_HOUR.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        service.publish(FIRST_HOUR.plusSeconds(3_600));
        double currentScore = totalScore(ideaId);

        assertThat(service.publish(FIRST_HOUR).published()).isFalse();
        assertThat(totalScore(ideaId)).isEqualTo(currentScore);
        assertThat(jdbc.queryForObject("SELECT max(target_hour) FROM ranking_runs", Instant.class))
                .isEqualTo(FIRST_HOUR.plusSeconds(3_600));
    }

    private RankingPublicationOutcome publishAfter(CountDownLatch start, Instant targetHour) {
        try {
            start.await();
            return service.publish(targetHour);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private RankingPublicationOutcome get(java.util.concurrent.Future<RankingPublicationOutcome> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private RankingResult result(UUID ideaId, int position, RankingScore score, Instant calculatedAt) {
        return new RankingResult(ideaId, position, score, 0, 0, 0, 0,
                calculatedAt.minusSeconds(86_400), calculatedAt);
    }

    private UUID publishedIdea(String suffix) {
        UUID authorId = user("author_" + suffix);
        UUID ideaId = UUID.randomUUID();
        Instant publishedAt = FIRST_HOUR.minusSeconds(2 * 86_400);
        jdbc.update("""
                INSERT INTO ideas(id, author_id, status, title, category, summary, problem,
                                  target_customer, solution, business_model, visibility,
                                  current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', ?, 'TECH', 'summary', 'problem',
                        'customer', 'solution', 'model', 'PUBLIC', 10, ?, ?, ?)
                """, ideaId, authorId, "idea " + suffix,
                Timestamp.from(publishedAt), Timestamp.from(publishedAt), Timestamp.from(publishedAt));
        return ideaId;
    }

    private UUID user(String suffix) {
        UUID id = UUID.randomUUID();
        Instant now = FIRST_HOUR.minusSeconds(200_000);
        String normalized = suffix.replaceAll("[^A-Za-z0-9_]", "_");
        jdbc.update("""
                INSERT INTO users(id, email, password_hash, profile_id, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'USER', 'ACTIVE', ?, ?)
                """, id, suffix + "-" + id + "@example.com",
                normalized.substring(0, Math.min(20, normalized.length())),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private void lockedLot(UUID ideaId, UUID userId, int principal, Instant purchasedAt) {
        jdbc.update("""
                INSERT INTO seed_unit_lots(id, idea_id, user_id, units, purchase_price, principal,
                                           purchased_at, unlocked_at, status, purchase_request_key)
                VALUES (?, ?, ?, ?, 10, ?, ?, ?, 'LOCKED', ?)
                """, UUID.randomUUID(), ideaId, userId, principal / 10, principal,
                Timestamp.from(purchasedAt), Timestamp.from(purchasedAt.plusSeconds(86_400)),
                UUID.randomUUID().toString());
    }

    private void feedback(UUID ideaId, UUID userId, boolean accepted) {
        Instant now = FIRST_HOUR.minusSeconds(1_000);
        jdbc.update("""
                INSERT INTO feedbacks(id, idea_id, user_id, feedback_type, content, accepted_at, created_at)
                VALUES (?, ?, ?, 'OTHER', ?, ?, ?)
                """, UUID.randomUUID(), ideaId, userId, "x".repeat(100),
                accepted ? Timestamp.from(now) : null, Timestamp.from(now));
    }

    private void like(UUID ideaId, UUID userId) {
        jdbc.update("INSERT INTO idea_likes(id, idea_id, user_id, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), ideaId, userId, Timestamp.from(FIRST_HOUR.minusSeconds(100)));
    }

    private void companyInterest(UUID ideaId, String suffix) {
        UUID userId = user(suffix);
        jdbc.update("UPDATE users SET role='COMPANY' WHERE id=?", userId);
        UUID companyProfileId = UUID.randomUUID();
        Instant now = FIRST_HOUR.minusSeconds(500);
        jdbc.update("""
                INSERT INTO company_profiles(
                    id, user_id, company_name, company_email, company_domain,
                    verified_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'example.com', ?, ?, ?)
                """, companyProfileId, userId, "Company " + suffix,
                suffix + "-" + companyProfileId + "@example.com",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO company_interests(id, idea_id, company_profile_id, interested_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), ideaId, companyProfileId, Timestamp.from(now));
    }

    private void views(UUID ideaId, long current, long delta, Instant bucketHour) {
        jdbc.update("""
                INSERT INTO idea_metric_current(idea_id, view_count, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (idea_id) DO UPDATE SET view_count=EXCLUDED.view_count, updated_at=EXCLUDED.updated_at
                """, ideaId, current, Timestamp.from(FIRST_HOUR));
        jdbc.update("""
                INSERT INTO idea_metric_hourly(idea_id, bucket_hour, view_delta, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (idea_id, bucket_hour) DO UPDATE
                SET view_delta=EXCLUDED.view_delta, updated_at=EXCLUDED.updated_at
                """, ideaId, Timestamp.from(bucketHour), delta, Timestamp.from(FIRST_HOUR));
    }

    private List<UUID> currentIdeaIdsInOrder() {
        return jdbc.queryForList("SELECT idea_id FROM ranking_current ORDER BY rank_position", UUID.class);
    }

    private double totalScore(UUID ideaId) {
        return jdbc.queryForObject("SELECT total_score FROM ranking_current WHERE idea_id=?", Double.class, ideaId);
    }

    private void cleanRankingAndFixtures() {
        jdbc.update("DELETE FROM ranking_current");
        jdbc.update("DELETE FROM ranking_runs");
        jdbc.update("DELETE FROM company_interests");
        jdbc.update("DELETE FROM idea_likes");
        jdbc.update("DELETE FROM feedback_revisions");
        jdbc.update("DELETE FROM contributions");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM idea_view_events");
        jdbc.update("DELETE FROM idea_metric_hourly");
        jdbc.update("DELETE FROM idea_metric_current");
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM company_verifications");
        jdbc.update("DELETE FROM company_profiles");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }
}
