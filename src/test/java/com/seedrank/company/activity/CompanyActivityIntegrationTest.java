package com.seedrank.company.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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
class CompanyActivityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM message_thread_messages");
        jdbc.update("DELETE FROM message_threads");
        jdbc.update("DELETE FROM company_interests");
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
    void verifiedCompanySeesOnlyOwnInterestsAndInquiriesNewestFirstWithSafeFields() throws Exception {
        CompanySession company = verifiedCompany("company@acme.test", "company_profile", "Acme");
        CompanySession other = verifiedCompany("other@beta.test", "other_profile", "Beta");
        IdeaFixture older = idea("older-author@example.com", "오래된 아이디어");
        IdeaFixture newer = idea("newer-author@example.com", "새 아이디어");
        Instant first = Instant.parse("2026-07-26T00:00:00Z");
        Instant second = Instant.parse("2026-07-26T01:00:00Z");

        insertInterest(company.companyProfileId(), older.id(), first);
        insertInterest(company.companyProfileId(), newer.id(), second);
        insertInterest(other.companyProfileId(), newer.id(), second.plusSeconds(1));
        UUID olderThread = insertThread(company.companyProfileId(), older, first);
        UUID newerThread = insertThread(company.companyProfileId(), newer, second);
        insertThread(other.companyProfileId(), newer, second.plusSeconds(1));

        String body = mockMvc.perform(get("/api/v1/me/company-activity")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + company.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(2))
                .andExpect(jsonPath("$.interests[0].ideaId").value(newer.id().toString()))
                .andExpect(jsonPath("$.interests[0].title").value("새 아이디어"))
                .andExpect(jsonPath("$.interests[0].interestedAt").value(second.toString()))
                .andExpect(jsonPath("$.interests[1].ideaId").value(older.id().toString()))
                .andExpect(jsonPath("$.inquiries.length()").value(2))
                .andExpect(jsonPath("$.inquiries[0].threadId").value(newerThread.toString()))
                .andExpect(jsonPath("$.inquiries[0].ideaId").value(newer.id().toString()))
                .andExpect(jsonPath("$.inquiries[0].ideaTitle").value("새 아이디어"))
                .andExpect(jsonPath("$.inquiries[0].updatedAt").value(second.toString()))
                .andExpect(jsonPath("$.inquiries[1].threadId").value(olderThread.toString()))
                .andExpect(jsonPath("$..companyEmail").doesNotExist())
                .andExpect(jsonPath("$..problem").doesNotExist())
                .andExpect(jsonPath("$..content").doesNotExist())
                .andExpect(jsonPath("$..read").doesNotExist())
                .andExpect(jsonPath("$..unreadCount").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(keys(response.get("interests").get(0)))
                .containsExactlyInAnyOrderElementsOf(Set.of("ideaId", "title", "interestedAt"));
        assertThat(keys(response.get("inquiries").get(0)))
                .containsExactlyInAnyOrderElementsOf(Set.of("threadId", "ideaId", "ideaTitle", "updatedAt"));
    }

    @Test
    void verifiedCompanyWithoutActivityGetsEmptyLists() throws Exception {
        CompanySession company = verifiedCompany("empty@acme.test", "empty_company", "Empty");

        mockMvc.perform(get("/api/v1/me/company-activity")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + company.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests").isArray())
                .andExpect(jsonPath("$.interests").isEmpty())
                .andExpect(jsonPath("$.inquiries").isArray())
                .andExpect(jsonPath("$.inquiries").isEmpty());
    }

    @Test
    void rejectsRegularUserPendingCompanyAndInvalidAuthentication() throws Exception {
        UserSession user = signupAndLogin("user@example.com", "regular_user");
        UserSession pending = signupAndLogin("pending@acme.test", "pending_company");
        UUID pendingProfileId = insertCompanyProfile(pending.userId(), "Pending", "pending@acme.test", null);
        assertThat(pendingProfileId).isNotNull();

        for (String token : new String[] {user.token(), pending.token()}) {
            mockMvc.perform(get("/api/v1/me/company-activity")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("VERIFIED_COMPANY_REQUIRED"));
        }
        mockMvc.perform(get("/api/v1/me/company-activity"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(get("/api/v1/me/company-activity")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void publishesCompanyActivityOpenApiWithoutReadState() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/me/company-activity'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyInterestActivityResponse.properties.ideaId").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyInquiryActivityResponse.properties.threadId").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyInquiryActivityResponse.properties.read").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CompanyInquiryActivityResponse.properties.unreadCount").doesNotExist());
    }

    private Set<String> keys(JsonNode node) {
        return objectMapper.convertValue(node, Map.class).keySet();
    }

    private CompanySession verifiedCompany(String email, String profileId, String companyName) throws Exception {
        UserSession user = signupAndLogin(email, profileId);
        UUID companyProfileId = insertCompanyProfile(user.userId(), companyName, email, Instant.now());
        jdbc.update("UPDATE users SET role='COMPANY' WHERE id=?", user.userId());
        return new CompanySession(user.userId(), user.token(), companyProfileId);
    }

    private UUID insertCompanyProfile(UUID userId, String companyName, String email, Instant verifiedAt) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String domain = email.substring(email.indexOf('@') + 1);
        jdbc.update("""
                INSERT INTO company_profiles
                    (id, user_id, company_name, company_email, company_domain, verified_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, userId, companyName, email, domain,
                verifiedAt == null ? null : Timestamp.from(verifiedAt), Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private IdeaFixture idea(String authorEmail, String title) {
        UUID authorId = insertUser(authorEmail, "author_" + UUID.randomUUID().toString().substring(0, 8));
        UUID ideaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        jdbc.update("""
                INSERT INTO ideas (id, author_id, status, title, category, summary, problem, target_customer,
                    solution, business_model, visibility, current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', ?, 'SERVICE', '요약', '비공개 문제', '고객',
                    '해결', '모델', 'PUBLIC', 10, ?, ?, ?)
                """, ideaId, authorId, title, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        return new IdeaFixture(ideaId, authorId);
    }

    private void insertInterest(UUID companyProfileId, UUID ideaId, Instant interestedAt) {
        jdbc.update("""
                INSERT INTO company_interests (id, idea_id, company_profile_id, interested_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), ideaId, companyProfileId, Timestamp.from(interestedAt));
    }

    private UUID insertThread(UUID companyProfileId, IdeaFixture idea, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO message_threads
                    (id, idea_id, company_profile_id, author_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, idea.id(), companyProfileId, idea.authorId(),
                Timestamp.from(updatedAt.minusSeconds(60)), Timestamp.from(updatedAt));
        return id;
    }

    private UserSession signupAndLogin(String email, String profileId) throws Exception {
        String signupBody = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(signupBody).get("userId").asText());
        String loginBody = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new UserSession(userId, objectMapper.readTree(loginBody).get("accessToken").asText());
    }

    private UUID insertUser(String email, String profileId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, profile_id, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'USER', 'ACTIVE', ?, ?)
                """, id, email, profileId, Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private record UserSession(UUID userId, String token) {}
    private record CompanySession(UUID userId, String token, UUID companyProfileId) {}
    private record IdeaFixture(UUID id, UUID authorId) {}
}
