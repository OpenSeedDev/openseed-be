package com.seedrank.feedback.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class FeedbackCreateIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM feedbacks");
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
    void removeFeedbackRowsForExistingFixtures() {
        jdbc.update("DELETE FROM feedbacks");
    }

    @Test
    void createsStructuredFeedbackWithOptionalEvidenceAndTwentyPointReward() throws Exception {
        String author = signupAndLogin("feedback-author@example.com", "feedback_author");
        String writer = signupAndLogin("feedback-writer@example.com", "feedback_writer");
        String ideaId = publishedIdea(author, "PUBLIC");
        String content = "구매 전환이 일어나지 않는 원인을 검증하려면 첫 화면에서 가격과 핵심 효익을 함께 보여주고, 버튼 클릭 이후 이탈률을 사용자군별로 비교해야 합니다. ".repeat(2);

        String body = create(writer, ideaId, "PROBLEM_EMPATHY", "  " + content + "  ",
                "https://example.com/evidence", "  인터뷰 12명의 공통 응답입니다.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ideaId").value(ideaId))
                .andExpect(jsonPath("$.authorId").exists())
                .andExpect(jsonPath("$.type").value("PROBLEM_EMPATHY"))
                .andExpect(jsonPath("$.content").value(content.strip()))
                .andExpect(jsonPath("$.evidenceUrl").value("https://example.com/evidence"))
                .andExpect(jsonPath("$.evidenceDescription").value("인터뷰 12명의 공통 응답입니다."))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.reward.originalAmount").value(20))
                .andExpect(jsonPath("$.reward.paidAmount").value(20))
                .andExpect(jsonPath("$.reward.expiredAmount").value(0))
                .andReturn().getResponse().getContentAsString();

        UUID feedbackId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        var row = jdbc.queryForMap("SELECT * FROM feedbacks WHERE id=?", feedbackId);
        assertThat(row).containsEntry("feedback_type", "PROBLEM_EMPATHY")
                .containsEntry("content", content.strip())
                .containsEntry("evidence_url", "https://example.com/evidence")
                .containsEntry("evidence_description", "인터뷰 12명의 공통 응답입니다.");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM point_ledgers WHERE source_type='FEEDBACK_CREATED' AND source_id=?",
                Integer.class, feedbackId)).isEqualTo(1);
    }

    @Test
    void allowsExactlyOneHundredCharactersAndOmittedEvidence() throws Exception {
        String author = signupAndLogin("boundary-author@example.com", "boundary_author");
        String writer = signupAndLogin("boundary-writer@example.com", "boundary_writer");
        String ideaId = publishedIdea(author, "PUBLIC");

        create(writer, ideaId, "TARGET_CUSTOMER", "가".repeat(100), null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("가".repeat(100)))
                .andExpect(jsonPath("$.evidenceUrl").doesNotExist())
                .andExpect(jsonPath("$.evidenceDescription").doesNotExist());
    }

    @Test
    void rejectsContentOutsideNormalizedLengthBoundary() throws Exception {
        String author = signupAndLogin("length-author@example.com", "length_author");
        String writer = signupAndLogin("length-writer@example.com", "length_writer");
        String ideaId = publishedIdea(author, "PUBLIC");

        create(writer, ideaId, "SOLUTION", "  " + "가".repeat(99) + "  ", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(writer, ideaId, "SOLUTION", "가".repeat(2001), null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedbacks", Integer.class)).isZero();
    }

    @Test
    void rejectsUnsupportedTypeAndUnsafeEvidenceUrl() throws Exception {
        String author = signupAndLogin("invalid-author@example.com", "invalid_author");
        String writer = signupAndLogin("invalid-writer@example.com", "invalid_writer");
        String ideaId = publishedIdea(author, "PUBLIC");

        create(writer, ideaId, "PRAISE", "가".repeat(100), null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(writer, ideaId, "COMPETITION", "가".repeat(100), "javascript:alert(1)", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresActiveAuthentication() throws Exception {
        String author = signupAndLogin("auth-author@example.com", "auth_author");
        String ideaId = publishedIdea(author, "PUBLIC");

        create(null, ideaId, "OTHER", "가".repeat(100), null, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        create("not-a-token", ideaId, "OTHER", "가".repeat(100), null, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void hidesDraftAndMissingIdeas() throws Exception {
        String author = signupAndLogin("draft-feedback-author@example.com", "draft_fb_author");
        String writer = signupAndLogin("draft-feedback-writer@example.com", "draft_fb_writer");
        String draftId = createDraft(author);

        create(writer, draftId, "OTHER", "가".repeat(100), null, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        create(writer, UUID.randomUUID().toString(), "OTHER", "가".repeat(100), null, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void acceptsFeedbackForAllThreePublishedVisibilities() throws Exception {
        String author = signupAndLogin("scope-author@example.com", "scope_author");
        String writer = signupAndLogin("scope-writer@example.com", "scope_writer");

        for (String visibility : new String[] {"PUBLIC", "SEMI_PUBLIC", "MATCHING"}) {
            String ideaId = publishedIdea(author, visibility);
            create(writer, ideaId, "BUSINESS_MODEL", "가".repeat(100), null, null)
                    .andExpect(status().isCreated());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedbacks", Integer.class)).isEqualTo(3);
    }

    @Test
    void sixthFeedbackIsSavedButItsDailyRewardExpires() throws Exception {
        String author = signupAndLogin("daily-author@example.com", "daily_author");
        String writer = signupAndLogin("daily-writer@example.com", "daily_writer");
        String ideaId = publishedIdea(author, "PUBLIC");

        for (int attempt = 1; attempt <= 6; attempt++) {
            create(writer, ideaId, "OTHER", ("피드백 " + attempt + " ").repeat(20), null, null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reward.paidAmount").value(attempt <= 5 ? 20 : 0))
                    .andExpect(jsonPath("$.reward.expiredAmount").value(attempt <= 5 ? 0 : 20));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedbacks", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT sum(paid_amount) FROM point_ledgers WHERE source_type='FEEDBACK_CREATED'", Integer.class))
                .isEqualTo(100);
        assertThat(jdbc.queryForObject(
                "SELECT sum(expired_amount) FROM point_ledgers WHERE source_type='FEEDBACK_CREATED'", Integer.class))
                .isEqualTo(20);
    }

    @Test
    void rollsBackFeedbackWhenPointRewardCannotBeRecorded() throws Exception {
        String author = signupAndLogin("rollback-author@example.com", "rollback_author");
        String writer = signupAndLogin("rollback-writer@example.com", "rollback_writer");
        String ideaId = publishedIdea(author, "PUBLIC");
        jdbc.update("DELETE FROM point_wallets WHERE user_id=?", userId(writer));

        create(writer, ideaId, "OTHER", "가".repeat(100), null, null)
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedbacks", Integer.class)).isZero();
    }

    @Test
    void publishesTheOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/feedbacks'].post.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.FeedbackCreateRequest.properties.type.enum.length()").value(6));
    }

    private ResultActions create(String token, String ideaId, String type, String content,
            String evidenceUrl, String evidenceDescription) throws Exception {
        var body = objectMapper.createObjectNode().put("type", type).put("content", content);
        if (evidenceUrl != null) body.put("evidenceUrl", evidenceUrl);
        if (evidenceDescription != null) body.put("evidenceDescription", evidenceDescription);
        var request = post("/api/v1/ideas/{ideaId}/feedbacks", ideaId)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mockMvc.perform(request);
    }

    private String publishedIdea(String authorToken, String visibility) throws Exception {
        String ideaId = createDraft(authorToken);
        mockMvc.perform(put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"이 문제를 실제로 겪고 있나요?\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ideas/{ideaId}/publish", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"%s\"}".formatted(visibility)))
                .andExpect(status().isOk());
        return ideaId;
    }

    private String createDraft(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"피드백 대상 아이디어","category":"SERVICE","summary":"요약",
                                 "problem":"문제","targetCustomer":"고객","solution":"해결","businessModel":"모델"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
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

    private UUID userId(String token) throws Exception {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return UUID.fromString(objectMapper.readTree(payload).get("sub").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
