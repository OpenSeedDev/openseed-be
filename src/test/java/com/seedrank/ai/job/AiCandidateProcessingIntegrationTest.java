package com.seedrank.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, AiCandidateProcessingIntegrationTest.ProviderConfiguration.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.ai.prompt-version=idea-candidates-v1"
})
@AutoConfigureMockMvc
class AiCandidateProcessingIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiCandidateProcessingService processor;
    @Autowired StubProvider provider;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        provider.response = AiCandidateResponseValidatorTest.validResponse();
        provider.failure = null;
        jdbc.update("DELETE FROM ai_generation_results");
        jdbc.update("DELETE FROM ai_jobs");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void storesRawAndNormalizedCandidatesAndCompletesWithoutPublishingAnIdea() throws Exception {
        UUID jobId = createJob("success");

        assertThat(processor.processNext("candidate-worker")).isEqualTo(AiCandidateProcessingOutcome.SUCCEEDED);

        var job = jdbc.queryForMap("SELECT status, lease_token, failure_code FROM ai_jobs WHERE id=?", jobId);
        assertThat(job).containsEntry("status", "SUCCEEDED").containsEntry("failure_code", null);
        assertThat(job.get("lease_token")).isNull();
        var result = jdbc.queryForMap(
                "SELECT raw_result::text AS raw_result, normalized_result::text AS normalized_result FROM ai_generation_results WHERE ai_job_id=?",
                jobId);
        assertThat(result.get("raw_result").toString()).contains("재고 폐기 문제");
        assertThat(result.get("normalized_result").toString()).contains("후보 1").doesNotContain("  후보 1  ");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ideas", Integer.class)).isZero();
        assertThat(provider.lastPromptVersion).isEqualTo("idea-candidates-v1");
        assertThat(provider.lastInput).contains("keyword", "background");
    }

    @Test
    void marksInvalidSchemaAsANonRetryableFailure() throws Exception {
        UUID invalidJob = createJob("invalid-schema");
        provider.response = "{\"problemAnalysis\":\"문제\",\"candidates\":[]}";

        assertThat(processor.processNext("schema-worker")).isEqualTo(AiCandidateProcessingOutcome.FAILED_INVALID_RESPONSE);
        assertThat(jdbc.queryForMap("SELECT status, failure_code, retry_count FROM ai_jobs WHERE id=?", invalidJob))
                .containsEntry("status", "FAILED")
                .containsEntry("failure_code", "INVALID_RESPONSE_SCHEMA")
                .containsEntry("retry_count", 0);
    }

    @ParameterizedTest
    @EnumSource(AiJobFailure.class)
    void retriesOnlySupportedProviderFailures(AiJobFailure failure) throws Exception {
        UUID retryJob = createJob("provider-" + failure.name().toLowerCase());
        provider.failure = new AiProviderException(failure);
        assertThat(processor.processNext("retry-worker")).isEqualTo(AiCandidateProcessingOutcome.RETRY_SCHEDULED);
        assertThat(jdbc.queryForMap("SELECT status, failure_code, retry_count FROM ai_jobs WHERE id=?", retryJob))
                .containsEntry("status", "RETRY_WAIT")
                .containsEntry("failure_code", null)
                .containsEntry("retry_count", 1);
    }

    private UUID createJob(String key) throws Exception {
        String email = key + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, "ai_" + Integer.toUnsignedString(key.hashCode()))))
                .andExpect(status().isCreated());
        var login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String token = json.readTree(login.getContentAsString()).get("accessToken").asText();
        var response = mockMvc.perform(post("/api/v1/ai/idea-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"재고 AI\",\"background\":\"폐기 문제\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).get("jobId").asText());
    }

    @TestConfiguration
    static class ProviderConfiguration {
        @Bean
        @Primary
        StubProvider stubProvider() {
            return new StubProvider();
        }
    }

    static class StubProvider implements AiCandidateProvider {
        String response;
        AiProviderException failure;
        String lastInput;
        String lastPromptVersion;

        @Override
        public String generate(String inputSnapshot, String promptVersion) {
            lastInput = inputSnapshot;
            lastPromptVersion = promptVersion;
            if (failure != null) throw failure;
            return response;
        }
    }
}
