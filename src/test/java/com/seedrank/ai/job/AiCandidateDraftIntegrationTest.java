package com.seedrank.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

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
        "app.ai.prompt-version=idea-candidates-v1",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class AiCandidateDraftIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiCandidateResponseValidator validator;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM idea_view_events");
        jdbc.update("DELETE FROM idea_metric_hourly");
        jdbc.update("DELETE FROM idea_metric_current");
        jdbc.update("DELETE FROM idea_likes");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM ai_generation_results");
        jdbc.update("DELETE FROM ai_jobs");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void ownerSelectsAndEditsACandidateIntoAnUnpublishedDraft() throws Exception {
        String token = signupAndLogin("select@example.com", "select_owner");
        UUID jobId = succeededJob(token, "select-success");

        var response = select(token, jobId, 2, "  편집한 재고 순환 서비스  ")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/ideas/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("편집한 재고 순환 서비스"))
                .andExpect(jsonPath("$.category").value("순환경제"))
                .andExpect(jsonPath("$.problem").value("편집한 문제 정의"))
                .andExpect(jsonPath("$.validationQuestions.length()").value(0))
                .andReturn().getResponse();

        UUID ideaId = UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
        var row = jdbc.queryForMap(
                "SELECT status, source_ai_job_id, source_ai_candidate_number FROM ideas WHERE id=?", ideaId);
        assertThat(row).containsEntry("status", "DRAFT")
                .containsEntry("source_ai_job_id", jobId)
                .containsEntry("source_ai_candidate_number", 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_versions WHERE idea_id=?", Long.class, ideaId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_timeline_events WHERE idea_id=?", Long.class, ideaId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledgers WHERE source_id=?", Long.class, ideaId)).isZero();
        assertThat(response.getContentAsString()).doesNotContain(
                "inputSnapshot", "promptVersion", "rawResult", "leaseToken", "sourceAiJobId");
    }

    @Test
    void hidesOtherOwnersAndMissingJobsAndRequiresAuthentication() throws Exception {
        String owner = signupAndLogin("owner@example.com", "candidate_owner");
        String other = signupAndLogin("other@example.com", "candidate_other");
        UUID jobId = succeededJob(owner, "private-result");

        select(other, jobId, 1, "다른 사람 선택")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_JOB_NOT_FOUND"));
        select(owner, UUID.randomUUID(), 1, "없는 작업 선택")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_JOB_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/ai/idea-jobs/{jobId}/draft", jobId)
                        .contentType(MediaType.APPLICATION_JSON).content(request(1, "인증 없음")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ideas", Long.class)).isZero();
    }

    @Test
    void onlySucceededJobsWithStoredResultsCanBeSelected() throws Exception {
        String token = signupAndLogin("state@example.com", "candidate_state");
        UUID pending = createJob(token, "pending");
        UUID failed = createJob(token, "failed");
        UUID missingResult = createJob(token, "missing-result");
        jdbc.update("UPDATE ai_jobs SET status='FAILED', failure_code='INVALID_RESPONSE_SCHEMA' WHERE id=?", failed);
        jdbc.update("UPDATE ai_jobs SET status='SUCCEEDED' WHERE id=?", missingResult);

        for (UUID jobId : List.of(pending, failed, missingResult)) {
            select(token, jobId, 1, "선택 불가")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AI_JOB_NOT_SELECTABLE"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ideas", Long.class)).isZero();
    }

    @Test
    void validatesCandidateNumberAndEditedContent() throws Exception {
        String token = signupAndLogin("validation@example.com", "candidate_validation");
        UUID jobId = succeededJob(token, "validation");

        select(token, jobId, 0, "유효한 제목").andExpect(status().isBadRequest());
        select(token, jobId, 6, "유효한 제목").andExpect(status().isBadRequest());
        select(token, jobId, 1, "   ").andExpect(status().isBadRequest());
        select(token, jobId, 1, "가".repeat(101)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ideas", Long.class)).isZero();
    }

    @Test
    void sequentialAndConcurrentSelectionLeaveOneDraftPerJob() throws Exception {
        String token = signupAndLogin("once@example.com", "candidate_once");
        UUID sequential = succeededJob(token, "sequential");
        select(token, sequential, 1, "첫 선택").andExpect(status().isCreated());
        select(token, sequential, 1, "반복 선택")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_JOB_ALREADY_SELECTED"));

        UUID concurrent = succeededJob(token, "concurrent");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> selectStatus(token, concurrent, "동시 선택 A", start));
            var second = executor.submit(() -> selectStatus(token, concurrent, "동시 선택 B", start));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ideas WHERE source_ai_job_id IN (?, ?)", Long.class, sequential, concurrent))
                .isEqualTo(2L);
    }

    @Test
    void publishesTheCandidateDraftOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}/draft'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}/draft'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}/draft'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}/draft'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}/draft'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}/draft'].post.responses['409']").exists());
    }

    private int selectStatus(String token, UUID jobId, String title, CountDownLatch start) throws Exception {
        start.await();
        return select(token, jobId, 3, title).andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions select(
            String token, UUID jobId, int candidateNumber, String title) throws Exception {
        return mockMvc.perform(post("/api/v1/ai/idea-jobs/{jobId}/draft", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(candidateNumber, title)));
    }

    private String request(int candidateNumber, String title) throws Exception {
        return json.writeValueAsString(java.util.Map.of(
                "candidateNumber", candidateNumber,
                "title", title,
                "category", "순환경제",
                "summary", "편집한 한 줄 요약",
                "problem", "편집한 문제 정의",
                "targetCustomer", "재고를 보유한 소상공인",
                "solution", "수요 예측과 순환 판매 연결",
                "businessModel", "거래 수수료와 구독"));
    }

    private UUID succeededJob(String token, String key) throws Exception {
        UUID jobId = createJob(token, key);
        String raw = AiCandidateResponseValidatorTest.validResponse();
        String normalized = validator.validateAndNormalize(raw);
        jdbc.update("UPDATE ai_jobs SET status='SUCCEEDED' WHERE id=?", jobId);
        jdbc.update("""
                INSERT INTO ai_generation_results(id, ai_job_id, raw_result, normalized_result, created_at)
                VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), now())
                """, UUID.randomUUID(), jobId, raw, normalized);
        return jobId;
    }

    private UUID createJob(String token, String key) throws Exception {
        var response = mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"후보 선택\",\"background\":\"선택 후 편집\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).get("jobId").asText());
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
}
