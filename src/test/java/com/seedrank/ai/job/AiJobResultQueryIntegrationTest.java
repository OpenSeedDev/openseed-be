package com.seedrank.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class AiJobResultQueryIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiCandidateResponseValidator validator;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM ai_generation_results");
        jdbc.update("DELETE FROM ai_jobs");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void ownerSeesPendingProcessingAndRetryWaitAsPublicStatuses() throws Exception {
        String token = signupAndLogin("owner@example.com", "job_owner");
        UUID pending = createJob(token, "pending");
        UUID processing = createJob(token, "processing");
        UUID retryWait = createJob(token, "retry-wait");
        jdbc.update("UPDATE ai_jobs SET status='PROCESSING', lease_owner='worker', lease_token=?, locked_until=now() + interval '2 minutes' WHERE id=?",
                UUID.randomUUID(), processing);
        jdbc.update("UPDATE ai_jobs SET status='RETRY_WAIT', next_attempt_at=now() + interval '30 seconds' WHERE id=?", retryWait);

        assertPublicState(token, pending, "PENDING");
        assertPublicState(token, processing, "PROCESSING");
        assertPublicState(token, retryWait, "PENDING");
    }

    @Test
    void ownerReceivesOnlyNormalizedResultWhenSucceeded() throws Exception {
        String token = signupAndLogin("success@example.com", "job_success");
        UUID jobId = createJob(token, "success");
        String raw = "{\"providerSecret\":\"must-not-leak\"}";
        String normalized = validator.validateAndNormalize(AiCandidateResponseValidatorTest.validResponse());
        jdbc.update("UPDATE ai_jobs SET status='SUCCEEDED' WHERE id=?", jobId);
        jdbc.update("INSERT INTO ai_generation_results(id, ai_job_id, raw_result, normalized_result, created_at) VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), now())",
                UUID.randomUUID(), jobId, raw, normalized);

        var response = mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.problemAnalysis").value("재고 폐기 문제"))
                .andExpect(jsonPath("$.result.candidates.length()").value(5))
                .andExpect(jsonPath("$.failureCode").isEmpty())
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertThat(body).doesNotContain("providerSecret", "inputSnapshot", "promptVersion", "retryCount",
                "leaseOwner", "leaseToken", "lockedUntil", "rawResult");
    }

    @Test
    void ownerReceivesOnlyFailureCodeWhenFailed() throws Exception {
        String token = signupAndLogin("failed@example.com", "job_failed");
        UUID jobId = createJob(token, "failed");
        jdbc.update("UPDATE ai_jobs SET status='FAILED', failure_code='INVALID_RESPONSE_SCHEMA' WHERE id=?", jobId);

        mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.result").isEmpty())
                .andExpect(jsonPath("$.failureCode").value("INVALID_RESPONSE_SCHEMA"));
    }

    @Test
    void hidesJobExistenceFromOtherUsersAndRejectsInvalidAuthentication() throws Exception {
        String ownerToken = signupAndLogin("owner@example.com", "job_owner");
        String otherToken = signupAndLogin("other@example.com", "job_other");
        UUID jobId = createJob(ownerToken, "private");

        mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_JOB_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_JOB_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", jobId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void publishesOwnerQueryOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs/{jobId}'].get.responses['404']").exists());
    }

    private void assertPublicState(String token, UUID jobId, String expected) throws Exception {
        mockMvc.perform(get("/api/v1/ai/idea-jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.result").isEmpty())
                .andExpect(jsonPath("$.failureCode").isEmpty())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    private UUID createJob(String token, String key) throws Exception {
        var response = mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"상태 조회\",\"background\":\"비동기 결과 확인\"}"))
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
