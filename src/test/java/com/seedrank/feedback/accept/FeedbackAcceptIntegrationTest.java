package com.seedrank.feedback.accept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class FeedbackAcceptIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        if (tableExists("contributions")) jdbc.update("DELETE FROM contributions");
        jdbc.update("DELETE FROM feedback_revisions");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM idea_view_events");
        jdbc.update("DELETE FROM idea_metric_hourly");
        jdbc.update("DELETE FROM idea_metric_current");
        jdbc.update("DELETE FROM idea_likes");
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void ideaAuthorAcceptsFeedbackAndAtomicallyCreatesContributionRewardAndTimeline() throws Exception {
        Account author = signupAndLogin("accept-author@example.com", "accept_author");
        Account contributor = signupAndLogin("accept-contributor@example.com", "accept_contributor");
        UUID ideaId = insertPublishedIdea(author.id());
        UUID feedbackId = insertFeedback(ideaId, contributor.id(), false, false);

        mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/accept", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.ideaId").value(ideaId.toString()))
                .andExpect(jsonPath("$.contributorId").value(contributor.id().toString()))
                .andExpect(jsonPath("$.contributionId").exists())
                .andExpect(jsonPath("$.acceptedAt").exists())
                .andExpect(jsonPath("$.reward.originalAmount").value(100))
                .andExpect(jsonPath("$.reward.paidAmount").value(100))
                .andExpect(jsonPath("$.reward.expiredAmount").value(0));

        assertThat(jdbc.queryForObject("SELECT accepted_at IS NOT NULL FROM feedbacks WHERE id=?",
                Boolean.class, feedbackId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contributions WHERE source_feedback_id=?",
                Integer.class, feedbackId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM point_ledgers
                WHERE source_type='FEEDBACK_ACCEPTED' AND source_id=? AND reward_scope_id=?
                """, Integer.class, feedbackId, ideaId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM idea_timeline_events
                WHERE event_type='FEEDBACK_ACCEPTED' AND source_id=? AND actor_id=?
                """, Integer.class, feedbackId, contributor.id())).isEqualTo(1);
    }

    @Test
    void requiresAuthenticationAndHidesFeedbackFromNonIdeaAuthor() throws Exception {
        Account author = signupAndLogin("hidden-author@example.com", "hidden_author");
        Account contributor = signupAndLogin("hidden-contributor@example.com", "hidden_contributor");
        Account other = signupAndLogin("hidden-other@example.com", "hidden_other");
        UUID feedbackId = insertFeedback(insertPublishedIdea(author.id()), contributor.id(), false, false);

        mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/accept", feedbackId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/accept", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT accepted_at IS NULL FROM feedbacks WHERE id=?",
                Boolean.class, feedbackId)).isTrue();
    }

    @Test
    void rejectsMissingDeletedAndAlreadyAcceptedFeedback() throws Exception {
        Account author = signupAndLogin("state-author@example.com", "state_author");
        Account contributor = signupAndLogin("state-contributor@example.com", "state_contributor");
        UUID ideaId = insertPublishedIdea(author.id());
        UUID deleted = insertFeedback(ideaId, contributor.id(), true, false);
        UUID accepted = insertFeedback(ideaId, contributor.id(), false, true);

        for (UUID id : List.of(UUID.randomUUID(), deleted)) {
            mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/accept", id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
        }
        mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/accept", accepted)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEEDBACK_ALREADY_ACCEPTED"));
    }

    @Test
    void onlyFirstContributionForSameContributorIdeaAndPolicyDateReceivesPoint() throws Exception {
        Account author = signupAndLogin("daily-accept-author@example.com", "daily_author");
        Account contributor = signupAndLogin("daily-accept-contributor@example.com", "daily_contributor");
        UUID ideaId = insertPublishedIdea(author.id());
        UUID first = insertFeedback(ideaId, contributor.id(), false, false);
        UUID second = insertFeedback(ideaId, contributor.id(), false, false);

        accept(author.token(), first).andExpect(status().isOk())
                .andExpect(jsonPath("$.reward.paidAmount").value(100));
        accept(author.token(), second).andExpect(status().isOk())
                .andExpect(jsonPath("$.reward.paidAmount").value(0))
                .andExpect(jsonPath("$.reward.expiredAmount").value(100));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM contributions WHERE idea_id=?",
                Integer.class, ideaId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT sum(paid_amount) FROM point_ledgers
                WHERE source_type='FEEDBACK_ACCEPTED' AND reward_scope_id=?
                """, Integer.class, ideaId)).isEqualTo(100);
        assertThat(jdbc.queryForObject("""
                SELECT sum(expired_amount) FROM point_ledgers
                WHERE source_type='FEEDBACK_ACCEPTED' AND reward_scope_id=?
                """, Integer.class, ideaId)).isEqualTo(100);
    }

    @Test
    void contributorCanReceiveRewardForAnotherIdeaOnSameDate() throws Exception {
        Account author = signupAndLogin("scope-author@example.com", "scope_author");
        Account contributor = signupAndLogin("scope-contributor@example.com", "scope_contributor");
        UUID firstIdea = insertPublishedIdea(author.id());
        UUID secondIdea = insertPublishedIdea(author.id());

        accept(author.token(), insertFeedback(firstIdea, contributor.id(), false, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reward.paidAmount").value(100));
        accept(author.token(), insertFeedback(secondIdea, contributor.id(), false, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reward.paidAmount").value(100));
    }

    @Test
    void anExpiredFirstRewardStillConsumesTheDailyContributorIdeaOccurrence() throws Exception {
        Account author = signupAndLogin("expired-author@example.com", "expired_author");
        Account contributor = signupAndLogin("expired-contributor@example.com", "expired_contributor");
        UUID ideaId = insertPublishedIdea(author.id());
        jdbc.update("UPDATE point_wallets SET balance=2000 WHERE user_id=?", contributor.id());

        accept(author.token(), insertFeedback(ideaId, contributor.id(), false, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reward.paidAmount").value(0))
                .andExpect(jsonPath("$.reward.expiredAmount").value(100));
        jdbc.update("UPDATE point_wallets SET balance=1900 WHERE user_id=?", contributor.id());
        accept(author.token(), insertFeedback(ideaId, contributor.id(), false, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reward.paidAmount").value(0))
                .andExpect(jsonPath("$.reward.expiredAmount").value(100));
    }

    @Test
    void concurrentAcceptanceOfSameFeedbackCreatesEveryEffectOnce() throws Exception {
        Account author = signupAndLogin("race-author@example.com", "race_author");
        Account contributor = signupAndLogin("race-contributor@example.com", "race_contributor");
        UUID feedbackId = insertFeedback(insertPublishedIdea(author.id()), contributor.id(), false, false);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> request = () -> {
                start.await();
                return mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/accept", feedbackId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                        .andReturn();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertThat(List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 409);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM contributions WHERE source_feedback_id=?",
                Integer.class, feedbackId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledgers WHERE source_id=?",
                Integer.class, feedbackId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_timeline_events WHERE source_id=?",
                Integer.class, feedbackId)).isEqualTo(1);
    }

    @Test
    void pointFailureRollsBackAcceptanceContributionAndTimeline() throws Exception {
        Account author = signupAndLogin("rollback-accept-author@example.com", "rollback_author");
        Account contributor = signupAndLogin("rollback-accept-contributor@example.com", "rollback_contrib");
        UUID feedbackId = insertFeedback(insertPublishedIdea(author.id()), contributor.id(), false, false);
        jdbc.update("DELETE FROM point_wallets WHERE user_id=?", contributor.id());

        accept(author.token(), feedbackId).andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT accepted_at IS NULL FROM feedbacks WHERE id=?",
                Boolean.class, feedbackId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contributions WHERE source_feedback_id=?",
                Integer.class, feedbackId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_timeline_events WHERE source_id=?",
                Integer.class, feedbackId)).isZero();
    }

    @Test
    void publishesFeedbackAcceptanceOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/accept'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/accept'].post.requestBody").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/accept'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/accept'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/accept'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/accept'].post.responses['409']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions accept(String token, UUID feedbackId) throws Exception {
        return mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/accept", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private Account signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
        return new Account(id, objectMapper.readTree(response).get("accessToken").asText());
    }

    private UUID insertPublishedIdea(UUID authorId) {
        UUID ideaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        jdbc.update("""
                INSERT INTO ideas (id, author_id, status, title, category, summary, problem, target_customer,
                    solution, business_model, visibility, current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', '채택 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델',
                    'PUBLIC', 10, ?, ?, ?)
                """, ideaId, authorId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        return ideaId;
    }

    private UUID insertFeedback(UUID ideaId, UUID contributorId, boolean deleted, boolean accepted) {
        UUID feedbackId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T01:00:00Z");
        jdbc.update("""
                INSERT INTO feedbacks (id, idea_id, user_id, feedback_type, content,
                    accepted_at, deleted_at, created_at)
                VALUES (?, ?, ?, 'OTHER', ?, ?, ?, ?)
                """, feedbackId, ideaId, contributorId, "구체적인 채택 대상 피드백입니다. ".repeat(10),
                accepted ? Timestamp.from(now) : null, deleted ? Timestamp.from(now) : null, Timestamp.from(now));
        return feedbackId;
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name=?)", Boolean.class, table));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Account(UUID id, String token) {}
}
