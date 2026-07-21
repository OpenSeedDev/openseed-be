package com.seedrank.idea.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
class IdeaArchiveIntegrationTest {

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
    void removeIdeaRowsForExistingFixtures() {
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void authorArchivesPublishedIdeaAndKeepsItInOwnDetailAndList() throws Exception {
        String token = signupAndLogin("archive@example.com", "archive_author");
        String ideaId = publishedIdea(token);

        archive(token, ideaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ideaId))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.updatedAt").exists());

        assertThat(jdbc.queryForObject("SELECT status FROM ideas WHERE id=?", String.class,
                UUID.fromString(ideaId))).isEqualTo("ARCHIVED");
        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/v1/me/ideas?status=ARCHIVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ideaId));
    }

    @Test
    void archivedIdeaIsHiddenFromGuestAndOtherUser() throws Exception {
        String author = signupAndLogin("hidden@example.com", "hidden_author");
        String other = signupAndLogin("viewer@example.com", "archive_viewer");
        String ideaId = publishedIdea(author);
        archive(author, ideaId).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedForeignAndMissingArchiveRequests() throws Exception {
        String author = signupAndLogin("owner@example.com", "archive_owner");
        String other = signupAndLogin("other@example.com", "archive_other");
        String ideaId = publishedIdea(author);

        archive(null, ideaId).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        archive(other, ideaId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        archive(author, UUID.randomUUID().toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void rejectsDraftAndAlreadyArchivedIdea() throws Exception {
        String token = signupAndLogin("state@example.com", "archive_state");
        String draftId = completedDraft(token);
        String publishedId = publishedIdea(token);

        archive(token, draftId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_PUBLISHED"));
        archive(token, publishedId).andExpect(status().isOk());
        archive(token, publishedId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_PUBLISHED"));
    }

    @Test
    void concurrentArchiveRequestsAreSerializedAndOpenApiPublishesContract() throws Exception {
        String token = signupAndLogin("concurrent@example.com", "archive_concurrent");
        String ideaId = publishedIdea(token);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> archiveStatusAfter(start, token, ideaId));
            var second = executor.submit(() -> archiveStatusAfter(start, token, ideaId));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM ideas WHERE id=?", String.class,
                UUID.fromString(ideaId))).isEqualTo("ARCHIVED");

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/archive'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/archive'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/archive'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/archive'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/archive'].post.responses['409']").exists());
    }

    private int archiveStatusAfter(CountDownLatch start, String token, String ideaId) throws Exception {
        start.await();
        return archive(token, ideaId).andReturn().getResponse().getStatus();
    }

    private ResultActions archive(String token, String ideaId) throws Exception {
        var request = post("/api/v1/ideas/{ideaId}/archive", ideaId);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mockMvc.perform(request);
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
                                {"title":"보관할 아이디어","category":"LOCAL_SERVICE",
                                 "summary":"보관 테스트 요약","problem":"해결할 문제",
                                 "targetCustomer":"대상 고객","solution":"해결 방법",
                                 "businessModel":"수익 모델"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ideaId = objectMapper.readTree(body).get("id").asText();
        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"고객이 실제로 이 문제를 겪는가?\"]}"))
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
