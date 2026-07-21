package com.seedrank.idea.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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
class IdeaDraftIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @AfterEach
    void removeIdeaRowsForExistingTestFixtures() {
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void createsAndReadsAnIdeaDraftWithoutAiOrPublicationFields() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "idea_author");
        UUID authorId = userId("author@example.com");

        var created = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDraft()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern("/api/v1/ideas/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("동네 빈 좌석 연결"))
                .andExpect(jsonPath("$.category").value("LOCAL_SERVICE"))
                .andExpect(jsonPath("$.summary").value("가까운 공유 공간의 빈 좌석을 연결한다."))
                .andExpect(jsonPath("$.problem").value("소규모 공유 공간의 빈 좌석이 발견되지 않는다."))
                .andExpect(jsonPath("$.targetCustomer").value("원격 근무자"))
                .andExpect(jsonPath("$.solution").value("시간대별 빈 좌석을 예약한다."))
                .andExpect(jsonPath("$.businessModel").value("예약 수수료"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse();

        JsonNode body = objectMapper.readTree(created.getContentAsString());
        String ideaId = body.get("id").asText();
        assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "status", "title", "category", "summary", "problem",
                "targetCustomer", "solution", "businessModel", "validationQuestions", "createdAt", "updatedAt");
        assertThat(body.get("validationQuestions").isEmpty()).isTrue();
        assertThat(body.toString()).doesNotContain(
                "author@example.com", "accessToken", "sessionId", "visibility", "aiJob");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM ideas WHERE id=?", UUID.fromString(ideaId));
        assertThat(row.get("author_id")).isEqualTo(authorId);
        assertThat(row.get("status")).isEqualTo("DRAFT");

        mockMvc.perform(get("/api/v1/ideas/{id}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ideaId))
                .andExpect(jsonPath("$.title").value("동네 빈 좌석 연결"));
    }

    @Test
    void normalizesOptionalBlankFieldsToNull() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "idea_author");

        mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  수동 초안  ",
                                  "category": "  ETC  ",
                                  "problem": "  해결할 문제  ",
                                  "summary": "  ",
                                  "targetCustomer": null,
                                  "solution": "",
                                  "businessModel": "   "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("수동 초안"))
                .andExpect(jsonPath("$.category").value("ETC"))
                .andExpect(jsonPath("$.problem").value("해결할 문제"))
                .andExpect(jsonPath("$.summary").isEmpty())
                .andExpect(jsonPath("$.targetCustomer").isEmpty())
                .andExpect(jsonPath("$.solution").isEmpty())
                .andExpect(jsonPath("$.businessModel").isEmpty());
    }

    @Test
    void rejectsMissingBlankAndOversizedDraftFields() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "idea_author");

        mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"category\":\"\",\"problem\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));

        mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "a".repeat(101),
                                "category", "b".repeat(51),
                                "problem", "c".repeat(2001),
                                "summary", "d".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresAValidActiveSessionForCreateAndRead() throws Exception {
        mockMvc.perform(post("/api/v1/ideas/drafts")
                        .contentType(MediaType.APPLICATION_JSON).content(validDraft()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(get("/api/v1/ideas/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void hidesDraftExistenceFromOtherUsersAndMissingIds() throws Exception {
        String authorToken = signupAndLogin("author@example.com", "idea_author");
        String otherToken = signupAndLogin("other@example.com", "idea_other");
        String ideaId = objectMapper.readTree(mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(validDraft()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/ideas/{id}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/ideas/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void publishesTheIdeaDraftOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/drafts'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/drafts'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/drafts'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/drafts'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.IdeaDraftRequest.properties.title.maxLength").value(100))
                .andExpect(jsonPath("$.components.schemas.IdeaDraftResponse.properties.status").exists());
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString()).get("accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
    }

    private String validDraft() {
        return """
                {
                  "title": "동네 빈 좌석 연결",
                  "category": "LOCAL_SERVICE",
                  "summary": "가까운 공유 공간의 빈 좌석을 연결한다.",
                  "problem": "소규모 공유 공간의 빈 좌석이 발견되지 않는다.",
                  "targetCustomer": "원격 근무자",
                  "solution": "시간대별 빈 좌석을 예약한다.",
                  "businessModel": "예약 수수료"
                }
                """;
    }
}
