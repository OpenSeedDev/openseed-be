package com.seedrank.feedback.manage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
class FeedbackManageIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        if (tableExists("feedback_revisions")) jdbc.update("DELETE FROM feedback_revisions");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.update("DELETE FROM idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void authorUpdatesFeedbackAndPreservesThePreviousSnapshot() throws Exception {
        String token = signupAndLogin("editor@example.com", "feedback_editor");
        UUID feedbackId = createFeedback(token, insertPublishedIdea(), "PROBLEM_EMPATHY", "이전 ".repeat(40),
                "https://example.com/old", "이전 근거");

        mockMvc.perform(put("/api/v1/feedbacks/{feedbackId}", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SOLUTION","content":"  %s  ",
                                 "evidenceUrl":"https://example.com/new","evidenceDescription":"  새 근거  "}
                                """.formatted("수정 ".repeat(40))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(feedbackId.toString()))
                .andExpect(jsonPath("$.type").value("SOLUTION"))
                .andExpect(jsonPath("$.content").value("수정 ".repeat(40).strip()))
                .andExpect(jsonPath("$.evidenceDescription").value("새 근거"))
                .andExpect(jsonPath("$.editedAt").exists());

        Map<String, Object> current = jdbc.queryForMap("SELECT * FROM feedbacks WHERE id=?", feedbackId);
        assertThat(current).containsEntry("feedback_type", "SOLUTION")
                .containsEntry("content", "수정 ".repeat(40).strip());
        Map<String, Object> history = jdbc.queryForMap("SELECT * FROM feedback_revisions WHERE feedback_id=?", feedbackId);
        assertThat(history).containsEntry("revision_type", "EDITED")
                .containsEntry("feedback_type", "PROBLEM_EMPATHY")
                .containsEntry("content", "이전 ".repeat(40).strip())
                .containsEntry("evidence_url", "https://example.com/old")
                .containsEntry("evidence_description", "이전 근거");
        assertThat(history.get("recorded_at")).isNotNull();
    }

    @Test
    void authorSoftDeletesFeedbackAndListNoLongerReturnsIt() throws Exception {
        String token = signupAndLogin("deleter@example.com", "feedback_deleter");
        UUID ideaId = insertPublishedIdea();
        UUID feedbackId = createFeedback(token, ideaId, "OTHER", "삭제 전 ".repeat(30), null, null);

        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM feedbacks WHERE id=?", Boolean.class, feedbackId))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT revision_type FROM feedback_revisions WHERE feedback_id=?", String.class, feedbackId))
                .isEqualTo("DELETED");
        mockMvc.perform(get("/api/v1/ideas/{ideaId}/feedbacks", ideaId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void nonAuthorCannotUpdateOrDeleteAndFeedbackExistenceIsHidden() throws Exception {
        String author = signupAndLogin("owner@example.com", "feedback_owner");
        String other = signupAndLogin("other@example.com", "feedback_other");
        UUID feedbackId = createFeedback(author, insertPublishedIdea(), "OTHER", "소유자 ".repeat(30), null, null);

        mockMvc.perform(put("/api/v1/feedbacks/{feedbackId}", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(validUpdateBody()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedback_revisions", Integer.class)).isZero();
    }

    @Test
    void missingOrAlreadyDeletedFeedbackCannotBeChanged() throws Exception {
        String token = signupAndLogin("missing@example.com", "feedback_missing");
        UUID feedbackId = createFeedback(token, insertPublishedIdea(), "OTHER", "삭제 대상 ".repeat(30), null, null);
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        for (UUID id : new UUID[] {feedbackId, UUID.randomUUID()}) {
            mockMvc.perform(put("/api/v1/feedbacks/{feedbackId}", id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(validUpdateBody()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
        }
    }

    @Test
    void requiresAuthenticationForUpdateAndDelete() throws Exception {
        UUID feedbackId = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/feedbacks/{feedbackId}", feedbackId)
                        .contentType(MediaType.APPLICATION_JSON).content(validUpdateBody()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void invalidUpdateDoesNotChangeFeedbackOrCreateHistory() throws Exception {
        String token = signupAndLogin("validator@example.com", "feedback_validator");
        UUID feedbackId = createFeedback(token, insertPublishedIdea(), "OTHER", "원본 내용 ".repeat(25), null, null);

        mockMvc.perform(put("/api/v1/feedbacks/{feedbackId}", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"content\":\"짧음\",\"evidenceUrl\":\"javascript:bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbc.queryForObject("SELECT content FROM feedbacks WHERE id=?", String.class, feedbackId))
                .isEqualTo("원본 내용 ".repeat(25).strip());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedback_revisions", Integer.class)).isZero();
    }

    @Test
    void publishesFeedbackManagementOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].put.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].delete.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}'].delete.responses['404']").exists());
    }

    private UUID createFeedback(String token, UUID ideaId, String type, String content,
            String evidenceUrl, String evidenceDescription) throws Exception {
        var body = objectMapper.createObjectNode().put("type", type).put("content", content);
        if (evidenceUrl != null) body.put("evidenceUrl", evidenceUrl);
        if (evidenceDescription != null) body.put("evidenceDescription", evidenceDescription);
        String response = mockMvc.perform(post("/api/v1/ideas/{ideaId}/feedbacks", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID insertPublishedIdea() {
        UUID ownerId = insertUser(UUID.randomUUID() + "@example.com", "owner_" + UUID.randomUUID().toString().substring(0, 8));
        UUID ideaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        jdbc.update("""
                INSERT INTO ideas (id, author_id, status, title, category, summary, problem, target_customer,
                    solution, business_model, visibility, current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', '피드백 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델',
                    'PUBLIC', 10, ?, ?, ?)
                """, ideaId, ownerId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        return ideaId;
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID insertUser(String email, String profileId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, profile_id, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'USER', 'ACTIVE', ?, ?)
                """, id, email, profileId, Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, tableName));
    }

    private String validUpdateBody() {
        return "{\"type\":\"SOLUTION\",\"content\":\"%s\"}".formatted("수정 내용 ".repeat(25));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
