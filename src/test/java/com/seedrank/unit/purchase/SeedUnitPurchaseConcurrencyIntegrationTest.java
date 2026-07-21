package com.seedrank.unit.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
class SeedUnitPurchaseConcurrencyIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void sequentialRetryReturnsOriginalPurchaseWithoutDuplicateSideEffects() throws Exception {
        String author = signupAndLogin("retry-author@example.com", "retry_author");
        String buyer = signupAndLogin("retry-buyer@example.com", "retry_buyer");
        String ideaId = publishedIdea(author);

        MvcResult first = purchase(buyer, ideaId, 4, 10, "retry-001");
        jdbc.update("UPDATE ideas SET current_unit_price=11 WHERE id=?", UUID.fromString(ideaId));
        MvcResult retried = purchase(buyer, ideaId, 4, 10, "retry-001");

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retried.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(first).get("lotId").asText()).isEqualTo(json(retried).get("lotId").asText());
        assertThat(json(retried).get("balanceAfter").asInt()).isEqualTo(290);
        assertPurchaseState(buyer, 1, 40, 290);
    }

    @Test
    void concurrentRetriesReturnOnePurchaseAndOneDebit() throws Exception {
        String author = signupAndLogin("same-author@example.com", "same_author");
        String buyer = signupAndLogin("same-buyer@example.com", "same_buyer");
        String ideaId = publishedIdea(author);

        List<MvcResult> results = concurrentPurchases(4, buyer, ideaId, 10, index -> "same-request");

        assertThat(results).allMatch(result -> result.getResponse().getStatus() == 201);
        assertThat(results.stream().map(this::lotId).distinct()).hasSize(1);
        assertThat(results).allMatch(result -> balanceAfter(result) == 230);
        assertPurchaseState(buyer, 1, 100, 230);
    }

    @Test
    void concurrentDistinctPurchasesPreserveBalanceAndDailyAndActiveLimits() throws Exception {
        String author = signupAndLogin("limit-author@example.com", "limit_author");
        String buyer = signupAndLogin("limit-buyer@example.com", "limit_buyer");
        String ideaId = publishedIdea(author);

        List<MvcResult> results = concurrentPurchases(4, buyer, ideaId, 10, index -> "limit-" + index);

        assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 201).count()).isEqualTo(3);
        assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 409).count()).isEqualTo(1);
        MvcResult rejected = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertThat(json(rejected).get("code").asText()).isEqualTo("PURCHASE_LIMIT_EXCEEDED");
        assertPurchaseState(buyer, 3, 300, 30);
    }

    @Test
    void rejectsReusingAKeyForDifferentPurchaseInputWithoutSideEffects() throws Exception {
        String author = signupAndLogin("reuse-author@example.com", "reuse_author");
        String buyer = signupAndLogin("reuse-buyer@example.com", "reuse_buyer");
        String ideaId = publishedIdea(author);

        assertThat(purchase(buyer, ideaId, 1, 10, "reused-key").getResponse().getStatus()).isEqualTo(201);
        MvcResult reused = purchase(buyer, ideaId, 2, 10, "reused-key");

        assertThat(reused.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(reused).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertPurchaseState(buyer, 1, 10, 320);
    }

    @Test
    void requiresANonBlankIdempotencyKeyOfAtMostOneHundredCharacters() throws Exception {
        String author = signupAndLogin("key-author@example.com", "key_author");
        String buyer = signupAndLogin("key-buyer@example.com", "key_buyer");
        String ideaId = publishedIdea(author);

        rawPurchase(buyer, ideaId, 1, 10, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        rawPurchase(buyer, ideaId, 1, 10, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        rawPurchase(buyer, ideaId, 1, 10, "a".repeat(101)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertPurchaseState(buyer, 0, 0, 330);
    }

    @Test
    void publishesRequiredIdempotencyHeaderContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/unit-purchases'].post.parameters[?(@.name == 'Idempotency-Key' && @.required == true)]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/unit-purchases'].post.responses['409']").exists());
    }

    private List<MvcResult> concurrentPurchases(
            int count, String token, String ideaId, int units, java.util.function.IntFunction<String> key) throws Exception {
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<MvcResult>>();
            for (int index = 0; index < count; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return purchase(token, ideaId, units, 10, key.apply(requestIndex));
                }));
            }
            ready.await();
            start.countDown();
            var results = new ArrayList<MvcResult>();
            for (var future : futures) results.add(future.get());
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult purchase(String token, String ideaId, int units, int confirmedPrice, String key) throws Exception {
        return rawPurchase(token, ideaId, units, confirmedPrice, key).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions rawPurchase(
            String token, String ideaId, int units, int confirmedPrice, String key) throws Exception {
        var request = post("/api/v1/ideas/{ideaId}/unit-purchases", ideaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"units\":%d,\"confirmedUnitPrice\":%d}".formatted(units, confirmedPrice));
        if (key != null) request.header(IDEMPOTENCY_KEY, key);
        return mockMvc.perform(request);
    }

    private JsonNode json(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String lotId(MvcResult result) {
        return json(result).get("lotId").asText();
    }

    private int balanceAfter(MvcResult result) {
        return json(result).get("balanceAfter").asInt();
    }

    private void assertPurchaseState(String token, int lotCount, int principal, int balance) throws Exception {
        UUID userId = userId(token);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seed_unit_lots WHERE user_id=?", Integer.class, userId))
                .isEqualTo(lotCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledgers WHERE user_id=? AND source_type='UNIT_PURCHASE'",
                Integer.class, userId)).isEqualTo(lotCount);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(principal), 0) FROM seed_unit_lots WHERE user_id=?",
                Integer.class, userId)).isEqualTo(principal);
        assertThat(jdbc.queryForObject("SELECT balance FROM point_wallets WHERE user_id=?", Integer.class, userId))
                .isEqualTo(balance);
    }

    private String publishedIdea(String authorToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"동시 구매 대상","category":"SERVICE","summary":"요약",
                                 "problem":"문제","targetCustomer":"고객","solution":"해결","businessModel":"모델"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ideaId = objectMapper.readTree(body).get("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"필요한가요?\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ideas/{ideaId}/publish", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk());
        return ideaId;
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
