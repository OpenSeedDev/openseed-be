package com.seedrank.messaging.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
class MessageThreadIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM message_threads");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM company_verifications");
        jdbc.update("DELETE FROM company_profiles");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void verifiedCompanyCreatesAThreadForEveryPublishedVisibilityWithoutLeakingDetails() throws Exception {
        String companyToken = verifiedCompany("company@acme.test", "acme_company", "Acme");

        for (String visibility : new String[] {"PUBLIC", "SEMI_PUBLIC", "MATCHING"}) {
            PublishedIdea idea = publishedIdea("author-" + visibility + "@example.com", visibility);

            String body = createThread(companyToken, idea.id())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isString())
                    .andExpect(jsonPath("$.ideaId").value(idea.id().toString()))
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andExpect(jsonPath("$.companyEmail").doesNotExist())
                    .andExpect(jsonPath("$.title").doesNotExist())
                    .andReturn().getResponse().getContentAsString();

            UUID threadId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
            assertThat(jdbc.queryForObject(
                    "SELECT author_id FROM message_threads WHERE id=?", UUID.class, threadId))
                    .isEqualTo(idea.authorId());
        }
    }

    @Test
    void repeatedCreationReturnsTheSameThreadAndStoresOneRow() throws Exception {
        String token = verifiedCompany("repeat@acme.test", "repeat_company", "Acme");
        PublishedIdea idea = publishedIdea("repeat-author@example.com", "PUBLIC");

        String first = createThread(token, idea.id()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = createThread(token, idea.id()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(first).get("id").asText())
                .isEqualTo(objectMapper.readTree(second).get("id").asText());
        assertThat(threadCount(idea.id())).isEqualTo(1);
    }

    @Test
    void concurrentCreationStoresOnlyOneThread() throws Exception {
        String token = verifiedCompany("race@acme.test", "race_company", "Acme");
        PublishedIdea idea = publishedIdea("race-author@example.com", "SEMI_PUBLIC");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createStatusAfter(start, token, idea.id()));
            var second = executor.submit(() -> createStatusAfter(start, token, idea.id()));
            start.countDown();
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 201);
        }
        assertThat(threadCount(idea.id())).isEqualTo(1);
    }

    @Test
    void requiresACompanyRoleAndVerifiedCompanyProfile() throws Exception {
        String userToken = signupAndLogin("user@example.com", "regular_user");
        String pendingToken = signupAndLogin("pending@acme.test", "pending_company");
        UUID pendingUser = userId("pending@acme.test");
        insertCompanyProfile(pendingUser, "Pending", "pending@acme.test", null);
        PublishedIdea idea = publishedIdea("role-author@example.com", "MATCHING");

        createThread(userToken, idea.id()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFIED_COMPANY_REQUIRED"));
        createThread(pendingToken, idea.id()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFIED_COMPANY_REQUIRED"));
        assertThat(threadCount(idea.id())).isZero();
    }

    @Test
    void requiresValidAuthentication() throws Exception {
        PublishedIdea idea = publishedIdea("auth-author@example.com", "PUBLIC");

        createThread(null, idea.id()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        createThread("not-a-token", idea.id()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void hidesMissingAndUnpublishedIdeasBehindTheSameNotFoundContract() throws Exception {
        String token = verifiedCompany("hidden@acme.test", "hidden_company", "Acme");
        UUID draftId = draftIdea("draft-author@example.com");

        createThread(token, draftId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        createThread(token, UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void publishesTheOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/message-thread'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/message-thread'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/message-thread'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/message-thread'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/message-thread'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/message-thread'].post.responses['404']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions createThread(String token, UUID ideaId) throws Exception {
        var request = post("/api/v1/ideas/{ideaId}/message-thread", ideaId);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(request);
    }

    private int createStatusAfter(CountDownLatch start, String token, UUID ideaId) throws Exception {
        start.await();
        return createThread(token, ideaId).andReturn().getResponse().getStatus();
    }

    private int threadCount(UUID ideaId) {
        return jdbc.queryForObject("SELECT count(*) FROM message_threads WHERE idea_id=?", Integer.class, ideaId);
    }

    private String verifiedCompany(String email, String profileId, String companyName) throws Exception {
        String token = signupAndLogin(email, profileId);
        UUID userId = userId(email);
        insertCompanyProfile(userId, companyName, email, Instant.now());
        jdbc.update("UPDATE users SET role='COMPANY' WHERE id=?", userId);
        return token;
    }

    private void insertCompanyProfile(UUID userId, String companyName, String email, Instant verifiedAt) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO company_profiles
                    (id, user_id, company_name, company_email, company_domain, verified_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, companyName, email, "acme.test",
                verifiedAt == null ? null : Timestamp.from(verifiedAt), Timestamp.from(now), Timestamp.from(now));
    }

    private PublishedIdea publishedIdea(String authorEmail, String visibility) throws Exception {
        UUID id = draftIdea(authorEmail);
        UUID authorId = jdbc.queryForObject("SELECT author_id FROM ideas WHERE id=?", UUID.class, id);
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE ideas SET status='PUBLISHED', visibility=?, current_unit_price=10,
                    published_at=?, updated_at=? WHERE id=?
                """, visibility, Timestamp.from(now), Timestamp.from(now), id);
        return new PublishedIdea(id, authorId);
    }

    private UUID draftIdea(String authorEmail) throws Exception {
        String token = signupAndLogin(authorEmail, "author_" + UUID.randomUUID().toString().substring(0, 8));
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"문의 대상\",\"category\":\"SERVICE\",\"problem\":\"문제\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
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

    private record PublishedIdea(UUID id, UUID authorId) {}
}
