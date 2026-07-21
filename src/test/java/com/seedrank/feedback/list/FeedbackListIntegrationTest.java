package com.seedrank.feedback.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class FeedbackListIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
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
    void listsAcceptedFeedbackFirstThenNewestAndExposesOnlyPublicAuthorIdentity() throws Exception {
        UUID ideaId = insertPublishedIdea();
        UUID authorId = insertUser("feedbacker@example.com", "public_feedbacker");
        UUID normal = insertFeedback(ideaId, authorId, "일반 최신", "2026-07-21T02:00:00Z", null, null);
        UUID acceptedOlder = insertFeedback(ideaId, authorId, "채택 과거", "2026-07-21T00:00:00Z", "2026-07-21T03:00:00Z", null);
        UUID acceptedNewer = insertFeedback(ideaId, authorId, "채택 최신", "2026-07-21T01:00:00Z", "2026-07-21T04:00:00Z", null);

        JsonNode response = getFeedbacks(ideaId, "");

        assertThat(response.at("/items/0/id").asText()).isEqualTo(acceptedNewer.toString());
        assertThat(response.at("/items/1/id").asText()).isEqualTo(acceptedOlder.toString());
        assertThat(response.at("/items/2/id").asText()).isEqualTo(normal.toString());
        assertThat(response.at("/items/0/authorProfileId").asText()).isEqualTo("public_feedbacker");
        assertThat(response.at("/items/0/accepted").asBoolean()).isTrue();
        assertThat(response.toString()).doesNotContain("feedbacker@example.com", authorId.toString(), "authorId");
    }

    @Test
    void cursorHasNoDuplicatesOrGapsAcrossAcceptedBoundaryAndEqualTimestamps() throws Exception {
        UUID ideaId = insertPublishedIdea();
        UUID authorId = insertUser("cursor@example.com", "cursor_writer");
        Instant sameTime = Instant.parse("2026-07-21T01:00:00Z");
        var expected = new HashSet<UUID>();
        expected.add(insertFeedback(ideaId, authorId, "채택1", sameTime.toString(), "2026-07-21T03:00:00Z", null));
        expected.add(insertFeedback(ideaId, authorId, "채택2", sameTime.toString(), "2026-07-21T04:00:00Z", null));
        expected.add(insertFeedback(ideaId, authorId, "일반1", sameTime.toString(), null, null));
        expected.add(insertFeedback(ideaId, authorId, "일반2", sameTime.toString(), null, null));

        JsonNode first = getFeedbacks(ideaId, "?size=2");
        JsonNode second = getFeedbacks(ideaId, "?size=2&cursor=" + first.get("nextCursor").asText());

        var actual = new HashSet<UUID>();
        first.get("items").forEach(item -> actual.add(UUID.fromString(item.get("id").asText())));
        second.get("items").forEach(item -> actual.add(UUID.fromString(item.get("id").asText())));
        assertThat(actual).isEqualTo(expected);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(second.get("hasNext").asBoolean()).isFalse();
        assertThat(second.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void excludesDeletedFeedbackAndReturnsAnEmptyPage() throws Exception {
        UUID ideaId = insertPublishedIdea();
        UUID authorId = insertUser("deleted@example.com", "deleted_writer");
        insertFeedback(ideaId, authorId, "삭제", "2026-07-21T01:00:00Z", null, "2026-07-21T02:00:00Z");

        mockMvc.perform(get("/api/v1/ideas/{ideaId}/feedbacks", ideaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void rejectsInvalidCursorAndPageSizeAndHidesDraftOrMissingIdea() throws Exception {
        UUID draftId = insertIdea("DRAFT");
        for (String query : new String[] {"cursor=invalid", "size=0", "size=101", "size=text"}) {
            mockMvc.perform(get("/api/v1/ideas/{ideaId}/feedbacks?" + query, insertPublishedIdea()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/api/v1/ideas/{ideaId}/feedbacks", draftId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/ideas/{ideaId}/feedbacks", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void publishesFeedbackListOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].get.parameters[?(@.name == 'cursor')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].get.parameters[?(@.name == 'size')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].get.responses['404']").exists());
    }

    private JsonNode getFeedbacks(UUID ideaId, String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/ideas/{ideaId}/feedbacks" + query, ideaId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private UUID insertPublishedIdea() {
        return insertIdea("PUBLISHED");
    }

    private UUID insertIdea(String status) {
        UUID ownerId = insertUser(UUID.randomUUID() + "@example.com", "owner_" + UUID.randomUUID().toString().substring(0, 8));
        UUID ideaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        jdbc.update("""
                INSERT INTO ideas (id, author_id, status, title, category, summary, problem, target_customer,
                    solution, business_model, visibility, current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, ?, '피드백 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델', ?, ?, ?, ?, ?)
                """, ideaId, ownerId, status, "PUBLISHED".equals(status) ? "PUBLIC" : null,
                "PUBLISHED".equals(status) ? 10 : null,
                "PUBLISHED".equals(status) ? Timestamp.from(now) : null, Timestamp.from(now), Timestamp.from(now));
        return ideaId;
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

    private UUID insertFeedback(UUID ideaId, UUID authorId, String marker, String createdAt,
            String acceptedAt, String deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO feedbacks (id, idea_id, user_id, feedback_type, content, accepted_at, deleted_at, created_at)
                VALUES (?, ?, ?, 'OTHER', ?, ?, ?, ?)
                """, id, ideaId, authorId, (marker + " ").repeat(50),
                acceptedAt == null ? null : Timestamp.from(Instant.parse(acceptedAt)),
                deletedAt == null ? null : Timestamp.from(Instant.parse(deletedAt)),
                Timestamp.from(Instant.parse(createdAt)));
        return id;
    }
}
