package com.seedrank.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Map;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
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
class AiJobCreationIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiJobCreationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @AfterEach
    void removeAiJobRowsForExistingTestFixtures() {
        jdbc.update("DELETE FROM ai_generation_results");
        jdbc.update("DELETE FROM ai_jobs");
    }

    @Test
    void acceptsAJobAndSnapshotsNormalizedInputAndServerPromptVersion() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "ai_author");

        var response = mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(IDEMPOTENCY_KEY, "idea-request-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": "  소상공인 재고  ",
                                  "background": "  폐기 직전 재고를 발견하기 어렵다.  "
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/api/v1/ai/idea-jobs/[0-9a-f-]+")))
                .andExpect(jsonPath("$.jobId").exists())
                .andReturn().getResponse();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.fieldNames()).toIterable().containsExactly("jobId");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT owner_id, status, input_snapshot::text AS input_snapshot, prompt_version, retry_count, created_at, updated_at FROM ai_jobs WHERE id=?",
                UUID.fromString(body.get("jobId").asText()));
        assertThat(row.get("owner_id")).isEqualTo(userId("author@example.com"));
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("input_snapshot").toString()).contains(
                "\"keyword\": \"소상공인 재고\"", "\"background\": \"폐기 직전 재고를 발견하기 어렵다.\"");
        assertThat(row.get("prompt_version")).isEqualTo("idea-candidates-v1");
        assertThat(row.get("retry_count")).isEqualTo(0);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void returnsTheSameJobForSequentialAndConcurrentReplays() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "ai_author");
        String firstJobId = createJob(accessToken, "replay-key", validRequest());
        assertThat(createJob(accessToken, "replay-key", validRequest())).isEqualTo(firstJobId);

        int requests = 6;
        var start = new CountDownLatch(1);
        var concurrentJobIds = new ArrayList<String>();
        try (var executor = Executors.newFixedThreadPool(requests)) {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (int index = 0; index < requests; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return createJob(accessToken, "concurrent-key", validRequest());
                }));
            }
            start.countDown();
            for (var future : futures) {
                concurrentJobIds.add(future.get());
            }
        }

        assertThat(concurrentJobIds).hasSize(requests).containsOnly(concurrentJobIds.getFirst());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ai_jobs WHERE owner_id=? AND idempotency_key=?",
                Integer.class, userId("author@example.com"), "concurrent-key")).isEqualTo(1);
    }

    @Test
    void rejectsReusingAKeyForDifferentInputWithoutChangingTheOriginalJob() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "ai_author");
        String originalJobId = createJob(accessToken, "conflicting-key", validRequest());

        mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(IDEMPOTENCY_KEY, "conflicting-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"다른 입력\",\"background\":\"다른 문제의식\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM ai_jobs", UUID.class).toString()).isEqualTo(originalJobId);
    }

    @Test
    void sameKeyReplayReturnsOriginalJobAfterServerPromptVersionChanges() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "ai_author");
        String originalJobId = createJob(accessToken, "version-change-key", validRequest());

        ReflectionTestUtils.setField(service, "promptVersion", "idea-candidates-v2");
        try {
            assertThat(createJob(accessToken, "version-change-key", validRequest())).isEqualTo(originalJobId);
        } finally {
            ReflectionTestUtils.setField(service, "promptVersion", "idea-candidates-v1");
        }
        assertThat(jdbc.queryForObject(
                "SELECT prompt_version FROM ai_jobs WHERE id=?", String.class, UUID.fromString(originalJobId)))
                .isEqualTo("idea-candidates-v1");
    }

    @Test
    void scopesIdempotencyKeysByOwner() throws Exception {
        String firstToken = signupAndLogin("first@example.com", "ai_first");
        String secondToken = signupAndLogin("second@example.com", "ai_second");

        String firstJobId = createJob(firstToken, "shared-key", validRequest());
        String secondJobId = createJob(secondToken, "shared-key", validRequest());

        assertThat(secondJobId).isNotEqualTo(firstJobId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs", Integer.class)).isEqualTo(2);
    }

    @Test
    void validatesInputAndIdempotencyKey() throws Exception {
        String accessToken = signupAndLogin("author@example.com", "ai_author");

        mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\" \",\"background\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(IDEMPOTENCY_KEY, " key-with-spaces ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(IDEMPOTENCY_KEY, "oversized-input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "keyword", "k".repeat(201), "background", "b".repeat(2001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresAValidActiveSession() throws Exception {
        mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(IDEMPOTENCY_KEY, "unauthenticated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void publishesTheAiJobCreationOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs'].post.parameters[?(@.name == 'Idempotency-Key')].required").value(true))
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/idea-jobs'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.AiJobCreationRequest.properties.keyword.maxLength").value(200))
                .andExpect(jsonPath("$.components.schemas.AiJobCreationRequest.properties.background.maxLength").value(2000))
                .andExpect(jsonPath("$.components.schemas.AiJobCreationResponse.properties.jobId").exists());
    }

    private String createJob(String accessToken, String idempotencyKey, String content) throws Exception {
        var response = mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(IDEMPOTENCY_KEY, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isAccepted())
                .andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString()).get("jobId").asText();
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

    private String validRequest() {
        return "{\"keyword\":\"소상공인 재고\",\"background\":\"폐기 직전 재고를 발견하기 어렵다.\"}";
    }
}
