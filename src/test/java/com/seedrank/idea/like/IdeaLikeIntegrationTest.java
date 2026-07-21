package com.seedrank.idea.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
class IdeaLikeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        if (tableExists("idea_likes")) jdbc.update("DELETE FROM idea_likes");
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
    void repeatedLikeAndUnlikeRequestsAreIdempotent() throws Exception {
        String token = signupAndLogin("like@example.com", "like_user");
        UUID ideaId = insertIdea("PUBLISHED", "PUBLIC");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(put("/api/v1/ideas/{ideaId}/like", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true))
                    .andExpect(jsonPath("$.likeCount").value(1));
        }
        assertThat(likeCount(ideaId)).isEqualTo(1);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/ideas/{ideaId}/like", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false))
                    .andExpect(jsonPath("$.likeCount").value(0));
        }
        assertThat(likeCount(ideaId)).isZero();
    }

    @Test
    void concurrentDuplicateLikesCreateOnlyOneRow() throws Exception {
        String token = signupAndLogin("concurrent-like@example.com", "concurrent_like");
        UUID ideaId = insertIdea("PUBLISHED", "PUBLIC");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> likeAfter(start, token, ideaId));
            var second = executor.submit(() -> likeAfter(start, token, ideaId));
            start.countDown();
            assertThat(first.get()).isEqualTo(200);
            assertThat(second.get()).isEqualTo(200);
        }
        assertThat(likeCount(ideaId)).isEqualTo(1);
    }

    @Test
    void supportsAllPublishedVisibilities() throws Exception {
        String token = signupAndLogin("visibility-like@example.com", "visibility_like");
        for (String visibility : new String[] {"PUBLIC", "SEMI_PUBLIC", "MATCHING"}) {
            UUID ideaId = insertIdea("PUBLISHED", visibility);
            mockMvc.perform(put("/api/v1/ideas/{ideaId}/like", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));
        }
    }

    @Test
    void rejectsDraftArchivedAndMissingIdeas() throws Exception {
        String token = signupAndLogin("state-like@example.com", "state_like");
        for (UUID ideaId : new UUID[] {
                insertIdea("DRAFT", null), insertIdea("ARCHIVED", "PUBLIC"), UUID.randomUUID()}) {
            mockMvc.perform(put("/api/v1/ideas/{ideaId}/like", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
            mockMvc.perform(delete("/api/v1/ideas/{ideaId}/like", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        }
    }

    @Test
    void requiresValidAuthentication() throws Exception {
        UUID ideaId = insertIdea("PUBLISHED", "PUBLIC");
        for (String authorization : new String[] {null, "Bearer invalid"}) {
            var request = put("/api/v1/ideas/{ideaId}/like", ideaId);
            if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        }
    }

    @Test
    void detailShowsPublicLikeCountAndViewerStateWithoutChangingVisibilityFields() throws Exception {
        String token = signupAndLogin("detail-like@example.com", "detail_like");
        UUID ideaId = insertIdea("PUBLISHED", "MATCHING");
        mockMvc.perform(put("/api/v1/ideas/{ideaId}/like", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.problem").doesNotExist())
                .andExpect(jsonPath("$.solution").doesNotExist());
        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.problem").doesNotExist())
                .andExpect(jsonPath("$.solution").doesNotExist());
    }

    @Test
    void publishesIdeaLikeOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/like'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/like'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/like'].put.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/like'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/like'].delete.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/like'].delete.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/like'].delete.responses['404']").exists());
    }

    private int likeAfter(CountDownLatch start, String token, UUID ideaId) throws Exception {
        start.await();
        return mockMvc.perform(put("/api/v1/ideas/{ideaId}/like", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getStatus();
    }

    private UUID insertIdea(String status, String visibility) {
        UUID ownerId = insertUser(UUID.randomUUID() + "@example.com", "owner_" + UUID.randomUUID().toString().substring(0, 8));
        UUID ideaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        jdbc.update("""
                INSERT INTO ideas (id, author_id, status, title, category, summary, problem, target_customer,
                    solution, business_model, visibility, current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, ?, '좋아요 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델', ?, ?, ?, ?, ?)
                """, ideaId, ownerId, status, visibility,
                "DRAFT".equals(status) ? null : 10,
                "DRAFT".equals(status) ? null : Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
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

    private int likeCount(UUID ideaId) {
        return jdbc.queryForObject("SELECT count(*) FROM idea_likes WHERE idea_id=?", Integer.class, ideaId);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, tableName));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
