package com.seedrank.idea.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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

import com.fasterxml.jackson.databind.JsonNode;
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
class IdeaPublishIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @AfterEach
    void removeIdeaRowsForExistingTestFixtures() {
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void publishesAllThreeVisibilitiesWithVersionPriceTimelineAndApplicableReward() throws Exception {
        for (String visibility : new String[] {"PUBLIC", "SEMI_PUBLIC", "MATCHING"}) {
            String suffix = visibility.toLowerCase().replace("_", "");
            String token = signupAndLogin(suffix + "@example.com", "author_" + suffix);
            String ideaId = completedDraft(token);

            mockMvc.perform(post("/api/v1/ideas/{ideaId}/publish", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"visibility\":\"%s\"}".formatted(visibility)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ideaId))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.visibility").value(visibility))
                    .andExpect(jsonPath("$.currentUnitPrice").value(10))
                    .andExpect(jsonPath("$.publishedAt").exists())
                    .andExpect(jsonPath("$.reward.originalAmount")
                            .value("MATCHING".equals(visibility) ? 0 : 50))
                    .andExpect(jsonPath("$.reward.paidAmount")
                            .value("MATCHING".equals(visibility) ? 0 : 50))
                    .andExpect(jsonPath("$.reward.expiredAmount").value(0));

            var idea = jdbc.queryForMap(
                    "SELECT status, visibility, current_unit_price, published_at FROM ideas WHERE id=?",
                    UUID.fromString(ideaId));
            assertThat(idea.get("status")).isEqualTo("PUBLISHED");
            assertThat(idea.get("visibility")).isEqualTo(visibility);
            assertThat(idea.get("current_unit_price")).isEqualTo(10);
            assertThat(idea.get("published_at")).isNotNull();

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM idea_versions WHERE idea_id=? AND version_number=1",
                    Integer.class, UUID.fromString(ideaId))).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM idea_timeline_events WHERE idea_id=? AND event_type='PUBLISHED'",
                    Integer.class, UUID.fromString(ideaId))).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM point_ledgers WHERE source_type='IDEA_PUBLISHED' AND source_id=?",
                    Integer.class, UUID.fromString(ideaId)))
                    .isEqualTo("MATCHING".equals(visibility) ? 0 : 1);
        }
    }

    @Test
    void versionOneStoresTheCompleteImmutableSnapshot() throws Exception {
        String token = signupAndLogin("snapshot@example.com", "snapshot_author");
        String ideaId = completedDraft(token);

        publish(token, ideaId, "PUBLIC").andExpect(status().isOk());

        var version = jdbc.queryForMap("SELECT * FROM idea_versions WHERE idea_id=?", UUID.fromString(ideaId));
        assertThat(version)
                .containsEntry("version_number", 1)
                .containsEntry("title", "동네 빈 좌석 연결")
                .containsEntry("category", "LOCAL_SERVICE")
                .containsEntry("summary", "빈 좌석과 대기 고객을 연결한다.")
                .containsEntry("problem", "빈 좌석이 발견되지 않는다.")
                .containsEntry("target_customer", "동네 매장과 대기 고객")
                .containsEntry("solution", "실시간으로 좌석을 연결한다.")
                .containsEntry("business_model", "매칭 수수료")
                .containsEntry("visibility", "PUBLIC");
        assertThat(version.get("editor_id")).isNotNull();
        assertThat(version.get("created_at")).isNotNull();
    }

    @Test
    void rejectsIncompleteContentOrMissingQuestionsWithoutAnySideEffects() throws Exception {
        String token = signupAndLogin("incomplete@example.com", "incomplete_author");
        String missingContentId = createDraft(token, false);
        setQuestions(token, missingContentId);
        String missingQuestionsId = createDraft(token, true);

        for (String ideaId : new String[] {missingContentId, missingQuestionsId}) {
            publish(token, ideaId, "PUBLIC")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDEA_NOT_READY_TO_PUBLISH"));
            assertNoPublishSideEffects(ideaId);
        }
    }

    @Test
    void requiresActiveAuthorAndHidesForeignOrMissingDrafts() throws Exception {
        String authorToken = signupAndLogin("owner@example.com", "owner_author");
        String otherToken = signupAndLogin("other@example.com", "other_author");
        String ideaId = completedDraft(authorToken);

        publish(null, ideaId, "PUBLIC")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        publish(otherToken, ideaId, "PUBLIC")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        publish(authorToken, UUID.randomUUID().toString(), "PUBLIC")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void rejectsUnsupportedVisibilityAndAlreadyPublishedIdea() throws Exception {
        String token = signupAndLogin("duplicate@example.com", "duplicate_author");
        String ideaId = completedDraft(token);

        publish(token, ideaId, "PRIVATE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        publish(token, ideaId, "SEMI_PUBLIC").andExpect(status().isOk());
        publish(token, ideaId, "SEMI_PUBLIC")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEA_ALREADY_PUBLISHED"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_versions WHERE idea_id=?", Integer.class,
                UUID.fromString(ideaId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_timeline_events WHERE idea_id=?", Integer.class,
                UUID.fromString(ideaId))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM point_ledgers WHERE source_type='IDEA_PUBLISHED' AND source_id=?",
                Integer.class, UUID.fromString(ideaId))).isEqualTo(1);
    }

    @Test
    void rewardsOnlyTwoPublicOrSemiPublicPublishesPerPolicyDay() throws Exception {
        String token = signupAndLogin("limit@example.com", "limit_author");

        for (int attempt = 1; attempt <= 3; attempt++) {
            String ideaId = completedDraft(token);
            publish(token, ideaId, attempt % 2 == 0 ? "SEMI_PUBLIC" : "PUBLIC")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reward.originalAmount").value(50))
                    .andExpect(jsonPath("$.reward.paidAmount").value(attempt <= 2 ? 50 : 0))
                    .andExpect(jsonPath("$.reward.expiredAmount").value(attempt <= 2 ? 0 : 50));
        }

        assertThat(jdbc.queryForObject(
                "SELECT sum(paid_amount) FROM point_ledgers WHERE source_type='IDEA_PUBLISHED'",
                Integer.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject(
                "SELECT sum(expired_amount) FROM point_ledgers WHERE source_type='IDEA_PUBLISHED'",
                Integer.class)).isEqualTo(50);
    }

    @Test
    void concurrentPublishCreatesOneVersionTimelineAndReward() throws Exception {
        String token = signupAndLogin("concurrent@example.com", "concurrent_author");
        String ideaId = completedDraft(token);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> publishStatusAfter(start, token, ideaId));
            var second = executor.submit(() -> publishStatusAfter(start, token, ideaId));
            start.countDown();
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }

        UUID id = UUID.fromString(ideaId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_versions WHERE idea_id=?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_timeline_events WHERE idea_id=?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM point_ledgers WHERE source_type='IDEA_PUBLISHED' AND source_id=?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void publishesTheOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/publish'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/publish'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/publish'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/publish'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/publish'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/publish'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.IdeaPublishRequest.properties.visibility.enum.length()")
                        .value(3));
    }

    private void assertNoPublishSideEffects(String ideaId) {
        UUID id = UUID.fromString(ideaId);
        assertThat(jdbc.queryForObject("SELECT status FROM ideas WHERE id=?", String.class, id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_versions WHERE idea_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_timeline_events WHERE idea_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM point_ledgers WHERE source_type='IDEA_PUBLISHED' AND source_id=?",
                Integer.class, id)).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions publish(String token, String ideaId, String visibility)
            throws Exception {
        var request = post("/api/v1/ideas/{ideaId}/publish", ideaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"%s\"}".formatted(visibility));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mockMvc.perform(request);
    }

    private int publishStatusAfter(CountDownLatch start, String token, String ideaId) throws Exception {
        start.await();
        return mockMvc.perform(post("/api/v1/ideas/{ideaId}/publish", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andReturn().getResponse().getStatus();
    }

    private String completedDraft(String token) throws Exception {
        String ideaId = createDraft(token, true);
        setQuestions(token, ideaId);
        return ideaId;
    }

    private String createDraft(String token, boolean complete) throws Exception {
        String content = complete ? """
                {"title":"동네 빈 좌석 연결","category":"LOCAL_SERVICE",
                 "summary":"빈 좌석과 대기 고객을 연결한다.","problem":"빈 좌석이 발견되지 않는다.",
                 "targetCustomer":"동네 매장과 대기 고객","solution":"실시간으로 좌석을 연결한다.",
                 "businessModel":"매칭 수수료"}
                """ : """
                {"title":"미완성 아이디어","category":"LOCAL_SERVICE","problem":"문제만 작성했다."}
                """;
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void setQuestions(String token, String ideaId) throws Exception {
        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"이 문제를 매주 겪는 사람은 몇 명인가?\"]}"))
                .andExpect(status().isOk());
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
