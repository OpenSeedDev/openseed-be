package com.seedrank.company.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
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
class CompanyInterestIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        if (tableExists("company_interests")) jdbc.update("DELETE FROM company_interests");
        jdbc.update("DELETE FROM message_threads");
        jdbc.update("DELETE FROM idea_likes");
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
    void verifiedCompanyCanIdempotentlyChangeInterestForEveryPublishedVisibility() throws Exception {
        String token = verifiedCompany("company@acme.test", "acme_company", "Acme");

        for (String visibility : new String[] {"PUBLIC", "SEMI_PUBLIC", "MATCHING"}) {
            UUID ideaId = insertIdea("PUBLISHED", visibility);

            putInterest(token, ideaId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.interested").value(true))
                    .andExpect(jsonPath("$.interestCount").value(1));
            putInterest(token, ideaId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.interestCount").value(1));
            deleteInterest(token, ideaId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.interested").value(false))
                    .andExpect(jsonPath("$.interestCount").value(0));
            deleteInterest(token, ideaId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.interestCount").value(0));

            assertThat(interestCount(ideaId)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM idea_timeline_events
                    WHERE idea_id=? AND event_type IN ('COMPANY_INTERESTED', 'COMPANY_INTEREST_REMOVED')
                    """, Integer.class, ideaId)).isEqualTo(2);
        }
    }

    @Test
    void concurrentRegistrationStoresOneInterestAndOneTimelineEvent() throws Exception {
        String token = verifiedCompany("race@acme.test", "race_company", "Race Corp");
        UUID ideaId = insertIdea("PUBLISHED", "PUBLIC");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> putStatusAfter(start, token, ideaId));
            var second = executor.submit(() -> putStatusAfter(start, token, ideaId));
            start.countDown();
            assertThat(first.get()).isEqualTo(200);
            assertThat(second.get()).isEqualTo(200);
        }

        assertThat(interestCount(ideaId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM idea_timeline_events
                WHERE idea_id=? AND event_type='COMPANY_INTERESTED'
                """, Integer.class, ideaId)).isEqualTo(1);
    }

    @Test
    void guestSeesCompanyNameAndInterestTimeOnlyForEveryVisibility() throws Exception {
        String token = verifiedCompany("public@acme.test", "public_company", "Public Company");

        for (String visibility : new String[] {"PUBLIC", "SEMI_PUBLIC", "MATCHING"}) {
            UUID ideaId = insertIdea("PUBLISHED", visibility);
            putInterest(token, ideaId).andExpect(status().isOk());

            String body = mockMvc.perform(get("/api/v1/ideas/{ideaId}/company-interests", ideaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].companyName").value("Public Company"))
                    .andExpect(jsonPath("$.items[0].interestedAt").isString())
                    .andExpect(jsonPath("$.interestCount").value(1))
                    .andExpect(jsonPath("$..companyEmail").doesNotExist())
                    .andExpect(jsonPath("$..companyDomain").doesNotExist())
                    .andReturn().getResponse().getContentAsString();

            var item = objectMapper.readTree(body).get("items").get(0);
            assertThat(objectMapper.convertValue(item, java.util.Map.class).keySet())
                    .containsExactlyInAnyOrderElementsOf(Set.of("companyName", "interestedAt"));
        }
    }

    @Test
    void interestListIsNewestFirst() throws Exception {
        UUID ideaId = insertIdea("PUBLISHED", "SEMI_PUBLIC");
        String first = verifiedCompany("first@acme.test", "first_company", "First");
        String second = verifiedCompany("second@beta.test", "second_company", "Second");
        putInterest(first, ideaId).andExpect(status().isOk());
        putInterest(second, ideaId).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ideas/{ideaId}/company-interests", ideaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].companyName").value("Second"))
                .andExpect(jsonPath("$.items[1].companyName").value("First"));
    }

    @Test
    void requiresVerifiedCompanyToChangeInterest() throws Exception {
        String userToken = signupAndLogin("user@example.com", "regular_user");
        String pendingToken = signupAndLogin("pending@acme.test", "pending_company");
        insertCompanyProfile(userId("pending@acme.test"), "Pending", "pending@acme.test", null);
        UUID ideaId = insertIdea("PUBLISHED", "MATCHING");

        for (String token : new String[] {userToken, pendingToken}) {
            putInterest(token, ideaId).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("VERIFIED_COMPANY_REQUIRED"));
            deleteInterest(token, ideaId).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("VERIFIED_COMPANY_REQUIRED"));
        }
        assertThat(interestCount(ideaId)).isZero();
    }

    @Test
    void rejectsInvalidAuthenticationAndHidesUnpublishedIdeas() throws Exception {
        String token = verifiedCompany("hidden@acme.test", "hidden_company", "Hidden");
        UUID draftId = insertIdea("DRAFT", null);
        UUID archivedId = insertIdea("ARCHIVED", "PUBLIC");

        putInterest(null, UUID.randomUUID()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        for (UUID ideaId : new UUID[] {draftId, archivedId, UUID.randomUUID()}) {
            putInterest(token, ideaId).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
            mockMvc.perform(get("/api/v1/ideas/{ideaId}/company-interests", ideaId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        }
    }

    @Test
    void publishesOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/company-interest'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/company-interest'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/company-interest'].put.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/company-interest'].put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/company-interest'].delete.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/company-interests'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/company-interests'].get.responses['404']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions putInterest(String token, UUID ideaId) throws Exception {
        var request = put("/api/v1/ideas/{ideaId}/company-interest", ideaId);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions deleteInterest(String token, UUID ideaId) throws Exception {
        return mockMvc.perform(delete("/api/v1/ideas/{ideaId}/company-interest", ideaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private int putStatusAfter(CountDownLatch start, String token, UUID ideaId) throws Exception {
        start.await();
        return putInterest(token, ideaId).andReturn().getResponse().getStatus();
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
        String domain = email.substring(email.indexOf('@') + 1);
        jdbc.update("""
                INSERT INTO company_profiles
                    (id, user_id, company_name, company_email, company_domain, verified_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, companyName, email, domain,
                verifiedAt == null ? null : Timestamp.from(verifiedAt), Timestamp.from(now), Timestamp.from(now));
    }

    private UUID insertIdea(String status, String visibility) {
        UUID ownerId = insertUser(UUID.randomUUID() + "@example.com", "owner_" + UUID.randomUUID().toString().substring(0, 8));
        UUID ideaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        jdbc.update("""
                INSERT INTO ideas (id, author_id, status, title, category, summary, problem, target_customer,
                    solution, business_model, visibility, current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, ?, '관심 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델', ?, ?, ?, ?, ?)
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

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
    }

    private int interestCount(UUID ideaId) {
        if (!tableExists("company_interests")) return 0;
        return jdbc.queryForObject("SELECT count(*) FROM company_interests WHERE idea_id=?", Integer.class, ideaId);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, tableName));
    }
}
