package com.seedrank.idea.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;

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
class IdeaUpdateIntegrationTest {

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
        jdbc.execute("DROP TRIGGER IF EXISTS fail_idea_version_insert ON idea_versions");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_idea_version_insert()");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void authorUpdatesPublishedIdeaAndStoresCompleteInternalSnapshot() throws Exception {
        String token = signupAndLogin("author@example.com", "update_author");
        String ideaId = publishedIdea(token, "SEMI_PUBLIC");

        update(token, ideaId, "수정된 제목")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ideaId))
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.visibility").value("SEMI_PUBLIC"))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.versionNumber").doesNotExist());

        UUID id = UUID.fromString(ideaId);
        Map<String, Object> idea = jdbc.queryForMap("SELECT * FROM ideas WHERE id=?", id);
        Map<String, Object> version = jdbc.queryForMap(
                "SELECT * FROM idea_versions WHERE idea_id=? AND version_number=2", id);
        assertThat(idea).containsEntry("title", "수정된 제목");
        assertThat(version)
                .containsEntry("title", "수정된 제목")
                .containsEntry("category", "LOCAL_SERVICE")
                .containsEntry("summary", "수정 요약")
                .containsEntry("problem", "수정 문제")
                .containsEntry("target_customer", "수정 고객")
                .containsEntry("solution", "수정 해결책")
                .containsEntry("business_model", "수정 수익 모델")
                .containsEntry("visibility", "SEMI_PUBLIC")
                .containsEntry("validation_questions", "이 문제를 매주 겪는 사람은 몇 명인가?");
        assertThat(version.get("editor_id")).isEqualTo(jdbc.queryForObject(
                "SELECT author_id FROM ideas WHERE id=?", UUID.class, id));
        assertThat(version.get("created_at")).isEqualTo(idea.get("updated_at"));
    }

    @Test
    void sequentialUpdatesKeepImmutableNumberedSnapshots() throws Exception {
        String token = signupAndLogin("sequential@example.com", "sequential_author");
        String ideaId = publishedIdea(token, "PUBLIC");

        update(token, ideaId, "두 번째 버전").andExpect(status().isOk());
        update(token, ideaId, "세 번째 버전").andExpect(status().isOk());

        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT version_number, title FROM idea_versions WHERE idea_id=? ORDER BY version_number",
                UUID.fromString(ideaId));
        assertThat(versions).extracting(row -> row.get("version_number")).containsExactly(1, 2, 3);
        assertThat(versions).extracting(row -> row.get("title"))
                .containsExactly("동네 빈 좌석 연결", "두 번째 버전", "세 번째 버전");
    }

    @Test
    void rejectsUnauthenticatedForeignMissingAndDraftUpdates() throws Exception {
        String author = signupAndLogin("owner@example.com", "owner_author");
        String other = signupAndLogin("other@example.com", "other_author");
        String publishedId = publishedIdea(author, "PUBLIC");
        String draftId = completedDraft(author);

        update(null, publishedId, "거부").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        update(other, publishedId, "거부").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        update(author, UUID.randomUUID().toString(), "거부").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        update(author, draftId, "거부").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_PUBLISHED"));
    }

    @Test
    void rejectsInvalidCompleteContentWithoutChangingIdeaOrVersion() throws Exception {
        String token = signupAndLogin("invalid@example.com", "invalid_author");
        String ideaId = publishedIdea(token, "PUBLIC");
        Timestamp before = jdbc.queryForObject("SELECT updated_at FROM ideas WHERE id=?", Timestamp.class,
                UUID.fromString(ideaId));

        mockMvc.perform(patch("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbc.queryForObject("SELECT updated_at FROM ideas WHERE id=?", Timestamp.class,
                UUID.fromString(ideaId))).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_versions WHERE idea_id=?", Integer.class,
                UUID.fromString(ideaId))).isEqualTo(1);
    }

    @Test
    void versionFailureRollsBackIdeaAndUpdatedAt() throws Exception {
        String token = signupAndLogin("rollback@example.com", "rollback_author");
        String ideaId = publishedIdea(token, "PUBLIC");
        Map<String, Object> before = jdbc.queryForMap("SELECT title, updated_at FROM ideas WHERE id=?",
                UUID.fromString(ideaId));
        jdbc.execute("""
                CREATE FUNCTION fail_idea_version_insert() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'test version failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_idea_version_insert BEFORE INSERT ON idea_versions
                FOR EACH ROW EXECUTE FUNCTION fail_idea_version_insert()
                """);

        update(token, ideaId, "롤백되어야 할 제목").andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForMap("SELECT title, updated_at FROM ideas WHERE id=?", UUID.fromString(ideaId)))
                .isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_versions WHERE idea_id=?", Integer.class,
                UUID.fromString(ideaId))).isEqualTo(1);
    }

    @Test
    void concurrentUpdatesAllocateDistinctVersionsAndKeepLatestSnapshotConsistent() throws Exception {
        String token = signupAndLogin("concurrent-update@example.com", "concurrent_update");
        String ideaId = publishedIdea(token, "MATCHING");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> updateStatusAfter(start, token, ideaId, "동시 제목 A"));
            var second = executor.submit(() -> updateStatusAfter(start, token, ideaId, "동시 제목 B"));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactly(200, 200);
        }

        UUID id = UUID.fromString(ideaId);
        assertThat(jdbc.queryForList(
                "SELECT version_number FROM idea_versions WHERE idea_id=? ORDER BY version_number", Integer.class, id))
                .containsExactly(1, 2, 3);
        String latestIdeaTitle = jdbc.queryForObject("SELECT title FROM ideas WHERE id=?", String.class, id);
        String latestVersionTitle = jdbc.queryForObject(
                "SELECT title FROM idea_versions WHERE idea_id=? AND version_number=3", String.class, id);
        assertThat(latestIdeaTitle).isEqualTo(latestVersionTitle);
    }

    @Test
    void versionsAreAppendOnlyAndOpenApiDoesNotExposeHistory() throws Exception {
        String token = signupAndLogin("append-only@example.com", "append_only_author");
        String ideaId = publishedIdea(token, "PUBLIC");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "UPDATE idea_versions SET title='변조' WHERE idea_id=?", UUID.fromString(ideaId)))
                .hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM idea_versions WHERE idea_id=?", UUID.fromString(ideaId)))
                .hasMessageContaining("append-only");

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].patch.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/versions']").doesNotExist());
    }

    private ResultActions update(String token, String ideaId, String title) throws Exception {
        var request = patch("/api/v1/ideas/{ideaId}", ideaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(title));
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mockMvc.perform(request);
    }

    private int updateStatusAfter(CountDownLatch start, String token, String ideaId, String title) throws Exception {
        start.await();
        return update(token, ideaId, title).andReturn().getResponse().getStatus();
    }

    private String updateBody(String title) {
        return """
                {"title":"%s","category":"LOCAL_SERVICE","summary":"수정 요약",
                 "problem":"수정 문제","targetCustomer":"수정 고객","solution":"수정 해결책",
                 "businessModel":"수정 수익 모델"}
                """.formatted(title);
    }

    private String publishedIdea(String token, String visibility) throws Exception {
        String ideaId = completedDraft(token);
        mockMvc.perform(post("/api/v1/ideas/{ideaId}/publish", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"%s\"}".formatted(visibility)))
                .andExpect(status().isOk());
        return ideaId;
    }

    private String completedDraft(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"동네 빈 좌석 연결","category":"LOCAL_SERVICE",
                                 "summary":"빈 좌석과 대기 고객을 연결한다.","problem":"빈 좌석이 발견되지 않는다.",
                                 "targetCustomer":"동네 매장과 대기 고객","solution":"실시간으로 좌석을 연결한다.",
                                 "businessModel":"매칭 수수료"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ideaId = objectMapper.readTree(body).get("id").asText();
        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"이 문제를 매주 겪는 사람은 몇 명인가?\"]}"))
                .andExpect(status().isOk());
        return ideaId;
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
