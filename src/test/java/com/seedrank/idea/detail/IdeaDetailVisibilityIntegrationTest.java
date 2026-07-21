package com.seedrank.idea.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MvcResult;

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
class IdeaDetailVisibilityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @AfterEach
    void removeIdeaRowsForExistingTestFixtures() {
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void publicIdeaExposesTheFullDetailToGuestUserAndCompany() throws Exception {
        String author = signupAndLogin("author@example.com", "detail_author");
        String user = signupAndLogin("user@example.com", "detail_user");
        String company = signupAndLogin("company@example.com", "detail_company");
        promoteToCompany("company@example.com");
        UUID ideaId = publishedIdea(author, "PUBLIC");

        assertFullDetail(read(ideaId, null));
        assertFullDetail(read(ideaId, user));
        assertFullDetail(read(ideaId, company));
    }

    @Test
    void semiPublicIdeaHidesDetailedFieldsFromGuestButShowsThemToAuthenticatedRoles() throws Exception {
        String author = signupAndLogin("author@example.com", "detail_author");
        String user = signupAndLogin("user@example.com", "detail_user");
        String company = signupAndLogin("company@example.com", "detail_company");
        promoteToCompany("company@example.com");
        UUID ideaId = publishedIdea(author, "SEMI_PUBLIC");

        JsonNode guest = read(ideaId, null);
        assertThat(guest.get("problem").asText()).isEqualTo("공개할 문제 정의");
        assertMissing(guest, "targetCustomer", "solution", "businessModel", "validationQuestions");
        assertFullDetail(read(ideaId, user));
        assertFullDetail(read(ideaId, company));
        assertFullDetail(read(ideaId, author));
    }

    @Test
    void matchingIdeaExposesOnlySummaryToGuestUserAndCompanyButFullDetailToAuthor() throws Exception {
        String author = signupAndLogin("author@example.com", "detail_author");
        String user = signupAndLogin("user@example.com", "detail_user");
        String company = signupAndLogin("company@example.com", "detail_company");
        promoteToCompany("company@example.com");
        UUID ideaId = publishedIdea(author, "MATCHING");

        for (String viewer : new String[] {null, user, company}) {
            JsonNode response = read(ideaId, viewer);
            assertThat(response.get("summary").asText()).isEqualTo("공개할 한 줄 요약");
            assertMissing(response, "problem", "targetCustomer", "solution", "businessModel", "validationQuestions");
        }
        assertFullDetail(read(ideaId, author));
    }

    @Test
    void draftRemainsAuthorOnlyAndInvalidOptionalTokenIsRejected() throws Exception {
        String author = signupAndLogin("author@example.com", "detail_author");
        String other = signupAndLogin("other@example.com", "detail_other");
        UUID ideaId = draftIdea(author);

        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        JsonNode authorView = read(ideaId, author);
        assertThat(authorView.get("problem").asText()).isEqualTo("공개할 문제 정의");
        assertThat(authorView.get("solution").asText()).isEqualTo("숨겨질 수 있는 해결책");
        assertThat(authorView.get("validationQuestions").isEmpty()).isTrue();
    }

    @Test
    void responseNeverLeaksIdentitySessionOrInternalVersionFields() throws Exception {
        String author = signupAndLogin("author@example.com", "detail_author");
        UUID ideaId = publishedIdea(author, "PUBLIC");

        String response = mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(
                "authorId", "author@example.com", "password", "accessToken", "sessionId",
                "versionNumber", "editorId");
    }

    @Test
    void publishesOptionalAuthenticationAndVisibilityResponseContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.IdeaDetailResponse.properties.visibility").exists())
                .andExpect(jsonPath("$.components.schemas.IdeaDetailResponse.properties.currentUnitPrice").exists());
    }

    private JsonNode read(UUID ideaId, String accessToken) throws Exception {
        var request = get("/api/v1/ideas/{ideaId}", ideaId);
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void assertFullDetail(JsonNode response) {
        assertThat(response.get("problem").asText()).isEqualTo("공개할 문제 정의");
        assertThat(response.get("targetCustomer").asText()).isEqualTo("대상 고객");
        assertThat(response.get("solution").asText()).isEqualTo("숨겨질 수 있는 해결책");
        assertThat(response.get("businessModel").asText()).isEqualTo("숨겨질 수 있는 수익 모델");
        assertThat(response.get("validationQuestions").size()).isEqualTo(1);
    }

    private void assertMissing(JsonNode response, String... fields) {
        assertThat(response.fieldNames()).toIterable().doesNotContain(fields);
    }

    private UUID publishedIdea(String authorToken, String visibility) throws Exception {
        UUID ideaId = draftIdea(authorToken);
        jdbc.update("""
                UPDATE ideas
                   SET status='PUBLISHED', visibility=?, current_unit_price=10,
                       published_at=now(), updated_at=now()
                 WHERE id=?
                """, visibility, ideaId);
        jdbc.update("INSERT INTO validation_questions(id, idea_id, question, sort_order) VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), ideaId, "이 아이디어가 문제를 해결하는가?");
        return ideaId;
    }

    private UUID draftIdea(String authorToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "공개 범위 검증 아이디어",
                                  "category": "SERVICE",
                                  "summary": "공개할 한 줄 요약",
                                  "problem": "공개할 문제 정의",
                                  "targetCustomer": "대상 고객",
                                  "solution": "숨겨질 수 있는 해결책",
                                  "businessModel": "숨겨질 수 있는 수익 모델"
                                }
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("accessToken").asText();
    }

    private void promoteToCompany(String email) {
        jdbc.update("UPDATE users SET role='COMPANY' WHERE email=?", email);
    }
}
