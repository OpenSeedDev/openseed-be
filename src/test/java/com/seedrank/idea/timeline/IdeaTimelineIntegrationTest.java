package com.seedrank.idea.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class IdeaTimelineIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        dropFailureTrigger();
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
    void cleanup() {
        dropFailureTrigger();
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void guestReadsPublishedEventWithoutInternalIdentityOrVersionContent() throws Exception {
        String token = signupAndLogin("timeline@example.com", "timeline_author");
        String ideaId = publishedIdea(token);

        mockMvc.perform(get("/api/v1/ideas/{ideaId}/timeline", ideaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].type").value("PUBLISHED"))
                .andExpect(jsonPath("$.events[0].actorProfileId").value("timeline_author"))
                .andExpect(jsonPath("$.events[0].occurredAt").exists())
                .andExpect(jsonPath("$.events[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.events[0].version").doesNotExist())
                .andExpect(jsonPath("$.events[0].content").doesNotExist());
    }

    @Test
    void updateAppendsEventAndReturnsChronologicalTimeline() throws Exception {
        String token = signupAndLogin("updated@example.com", "timeline_updated");
        String ideaId = publishedIdea(token);

        update(token, ideaId, "수정된 아이디어");

        mockMvc.perform(get("/api/v1/ideas/{ideaId}/timeline", ideaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].type").value("PUBLISHED"))
                .andExpect(jsonPath("$.events[1].type").value("UPDATED"));
    }

    @Test
    void hidesDraftAndArchivedIdeaFromNonAuthorButAuthorKeepsArchivedTimeline() throws Exception {
        String author = signupAndLogin("owner@example.com", "timeline_owner");
        String other = signupAndLogin("other@example.com", "timeline_other");
        String draftId = completedDraft(author);
        String publishedId = publishedIdea(author);
        mockMvc.perform(post("/api/v1/ideas/{ideaId}/archive", publishedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ideas/{ideaId}/timeline", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ideas/{ideaId}/timeline", publishedId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ideas/{ideaId}/timeline", publishedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ideas/{ideaId}/timeline", publishedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].type").value("PUBLISHED"));
    }

    @Test
    void timelineInsertFailureRollsBackIdeaUpdateAndVersion() throws Exception {
        String token = signupAndLogin("rollback@example.com", "timeline_rollback");
        String ideaId = publishedIdea(token);
        UUID id = UUID.fromString(ideaId);
        String beforeTitle = jdbc.queryForObject("SELECT title FROM ideas WHERE id=?", String.class, id);
        jdbc.execute("""
                CREATE FUNCTION fail_updated_timeline() RETURNS trigger AS $$
                BEGIN
                  IF NEW.event_type = 'UPDATED' THEN RAISE EXCEPTION 'test timeline failure'; END IF;
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_updated_timeline BEFORE INSERT ON idea_timeline_events
                FOR EACH ROW EXECUTE FUNCTION fail_updated_timeline()
                """);

        mockMvc.perform(patch("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("롤백 제목")))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT title FROM ideas WHERE id=?", String.class, id)).isEqualTo(beforeTitle);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_versions WHERE idea_id=?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT event_type FROM idea_timeline_events WHERE idea_id=? ORDER BY created_at, id", String.class, id))
                .containsExactly("PUBLISHED");
    }

    @Test
    void openApiPublishesTimelineContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/timeline'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/timeline'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/timeline'].get.responses['404']").exists());
    }

    private void update(String token, String ideaId, String title) throws Exception {
        mockMvc.perform(patch("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(title)))
                .andExpect(status().isOk());
    }

    private String updateBody(String title) {
        return """
                {"title":"%s","category":"LOCAL_SERVICE","summary":"수정 요약",
                 "problem":"수정 문제","targetCustomer":"수정 고객","solution":"수정 해결책",
                 "businessModel":"수정 수익 모델"}
                """.formatted(title);
    }

    private String publishedIdea(String token) throws Exception {
        String ideaId = completedDraft(token);
        mockMvc.perform(post("/api/v1/ideas/{ideaId}/publish", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk());
        return ideaId;
    }

    private String completedDraft(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"타임라인 아이디어","category":"LOCAL_SERVICE",
                                 "summary":"성장 과정 요약","problem":"기록할 문제",
                                 "targetCustomer":"대상 고객","solution":"해결 방법","businessModel":"수익 모델"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ideaId = objectMapper.readTree(body).get("id").asText();
        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"성장 여부를 어떻게 확인하는가?\"]}"))
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

    private void dropFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_updated_timeline ON idea_timeline_events");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_updated_timeline()");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
