package com.seedrank.idea.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ValidationQuestionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @AfterEach
    void removeIdeaRowsForExistingTestFixtures() {
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void authorSavesOneToThreeQuestionsAndReadsThemInIdeaDetails() throws Exception {
        String token = signupAndLogin("author@example.com", "idea_author");
        String ideaId = createIdea(token);

        var response = mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questions":[
                                  "이 문제를 매주 겪는 사람은 몇 명인가?",
                                  "현재 대안에 월 얼마를 지불하는가?",
                                  "예약 전환율이 20%를 넘는가?"
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].id").exists())
                .andExpect(jsonPath("$.questions[0].question").value("이 문제를 매주 겪는 사람은 몇 명인가?"))
                .andExpect(jsonPath("$.questions[0].sortOrder").value(1))
                .andExpect(jsonPath("$.questions[2].sortOrder").value(3))
                .andReturn().getResponse();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.toString()).doesNotContain("author@example.com", "accessToken", "sessionId");

        mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationQuestions.length()").value(3))
                .andExpect(jsonPath("$.validationQuestions[0].question")
                        .value("이 문제를 매주 겪는 사람은 몇 명인가?"))
                .andExpect(jsonPath("$.validationQuestions[1].sortOrder").value(2));

        var rows = jdbc.queryForList(
                "SELECT question, sort_order FROM validation_questions WHERE idea_id=? ORDER BY sort_order",
                UUID.fromString(ideaId));
        assertThat(rows).extracting(row -> row.get("sort_order")).containsExactly(1, 2, 3);
    }

    @Test
    void replacesEditsTrimsAndReordersTheWholeQuestionList() throws Exception {
        String token = signupAndLogin("author@example.com", "idea_author");
        String ideaId = createIdea(token);
        replace(token, ideaId, "{\"questions\":[\"첫 질문\",\"둘째 질문\",\"셋째 질문\"]}");

        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"  셋째 질문 수정  \",\"첫 질문\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].question").value("셋째 질문 수정"))
                .andExpect(jsonPath("$.questions[0].sortOrder").value(1))
                .andExpect(jsonPath("$.questions[1].question").value("첫 질문"))
                .andExpect(jsonPath("$.questions[1].sortOrder").value(2));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM validation_questions WHERE idea_id=?", Integer.class,
                UUID.fromString(ideaId))).isEqualTo(2);
    }

    @Test
    void rejectsZeroFourBlankAndNullQuestionsWithoutReplacingExistingQuestions() throws Exception {
        String token = signupAndLogin("author@example.com", "idea_author");
        String ideaId = createIdea(token);
        replace(token, ideaId, "{\"questions\":[\"기존 질문\"]}");

        for (String invalid : new String[] {
                "{\"questions\":[]}",
                "{\"questions\":[\"1\",\"2\",\"3\",\"4\"]}",
                "{\"questions\":[\"   \"]}",
                "{\"questions\":[null]}"
        }) {
            mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        assertThat(jdbc.queryForObject(
                "SELECT question FROM validation_questions WHERE idea_id=?", String.class,
                UUID.fromString(ideaId))).isEqualTo("기존 질문");
    }

    @Test
    void requiresAnActiveSessionAndHidesIdeasFromOtherAuthors() throws Exception {
        String authorToken = signupAndLogin("author@example.com", "idea_author");
        String otherToken = signupAndLogin("other@example.com", "idea_other");
        String ideaId = createIdea(authorToken);

        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"questions\":[\"질문\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"questions\":[\"질문\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"questions\":[\"질문\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void publishesTheValidationQuestionOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/validation-questions'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/validation-questions'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/validation-questions'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/validation-questions'].put.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/validation-questions'].put.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationQuestionRequest.properties.questions.minItems").value(1))
                .andExpect(jsonPath("$.components.schemas.ValidationQuestionRequest.properties.questions.maxItems").value(3))
                .andExpect(jsonPath("$.components.schemas.IdeaDraftResponse.properties.validationQuestions").exists());
    }

    private void replace(String token, String ideaId, String json) throws Exception {
        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private String createIdea(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"동네 빈 좌석 연결",
                                  "category":"LOCAL_SERVICE",
                                  "problem":"빈 좌석이 발견되지 않는다."
                                }
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
