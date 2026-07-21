package com.seedrank.point.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
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
        "springdoc.api-docs.enabled=true",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.auth.cookie-secure=false"
})
@AutoConfigureMockMvc
class PointWalletQueryIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void returnsCurrentWalletAndPendingRecoveryBalance() throws Exception {
        String token = signupAndLogin("member@example.com", "seed_member");

        mockMvc.perform(authenticatedGet("/api/v1/me/wallet", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(400))
                .andExpect(jsonPath("$.pendingRecoveryBalance").value(0))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void returnsDailyAccessAndSignupLedgersWithCompleteImmutableAccountingFields() throws Exception {
        String token = signupAndLogin("member@example.com", "seed_member");
        UUID userId = userId("member@example.com");

        mockMvc.perform(authenticatedGet("/api/v1/me/point-ledgers", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].originalAmount").value(100))
                .andExpect(jsonPath("$.items[0].paidAmount").value(100))
                .andExpect(jsonPath("$.items[0].expiredAmount").value(0))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(400))
                .andExpect(jsonPath("$.items[0].sourceType").value("DAILY_FIRST_ACCESS"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].originalAmount").value(300))
                .andExpect(jsonPath("$.items[1].paidAmount").value(300))
                .andExpect(jsonPath("$.items[1].balanceAfter").value(300))
                .andExpect(jsonPath("$.items[1].sourceType").value("SIGNUP_BONUS"))
                .andExpect(jsonPath("$.items[1].sourceId").value(userId.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void cursorPaginationHasNoDuplicatesOrGapsIncludingEqualTimestamps() throws Exception {
        String token = signupAndLogin("member@example.com", "seed_member");
        UUID userId = userId("member@example.com");
        Instant sameTime = Instant.parse("2026-01-01T00:00:00Z");
        insertLedger(userId, UUID.fromString("00000000-0000-0000-0000-000000000001"), sameTime, 301);
        insertLedger(userId, UUID.fromString("00000000-0000-0000-0000-000000000002"), sameTime, 302);
        insertLedger(userId, UUID.fromString("00000000-0000-0000-0000-000000000003"), sameTime, 303);

        JsonNode first = getJson("/api/v1/me/point-ledgers?size=3", token);
        assertThat(first.get("items").size()).isEqualTo(3);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        String cursor = first.get("nextCursor").asText();

        JsonNode second = getJson("/api/v1/me/point-ledgers?size=3&cursor=" + cursor, token);
        assertThat(second.get("items").size()).isEqualTo(2);
        assertThat(second.get("hasNext").asBoolean()).isFalse();
        assertThat(second.get("nextCursor").isNull()).isTrue();

        List<String> ids = java.util.stream.Stream.of(first, second)
                .flatMap(page -> java.util.stream.StreamSupport.stream(page.get("items").spliterator(), false))
                .map(item -> item.get("id").asText())
                .toList();
        assertThat(ids).hasSize(5);
        assertThat(new HashSet<>(ids)).hasSize(5);
    }

    @Test
    void validatesPageSizeAndCursor() throws Exception {
        String token = signupAndLogin("member@example.com", "seed_member");

        for (String query : List.of("size=0", "size=101", "size=abc", "cursor=not-a-valid-cursor")) {
            mockMvc.perform(authenticatedGet("/api/v1/me/point-ledgers?" + query, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(authenticatedGet("/api/v1/me/point-ledgers?size=1", token))
                .andExpect(status().isOk());
        mockMvc.perform(authenticatedGet("/api/v1/me/point-ledgers?size=100", token))
                .andExpect(status().isOk());
    }

    @Test
    void requiresValidAuthenticationAndNeverLeaksAnotherUsersWallet() throws Exception {
        String firstToken = signupAndLogin("first@example.com", "same_profile");
        signupAndLogin("second@example.com", "same_profile");
        jdbc.update("UPDATE point_wallets SET balance=777 WHERE user_id=?", userId("second@example.com"));

        mockMvc.perform(get("/api/v1/me/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(authenticatedGet("/api/v1/me/wallet", firstToken + "forged"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(authenticatedGet("/api/v1/me/wallet", firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(400));
        mockMvc.perform(authenticatedGet("/api/v1/me/point-ledgers", firstToken))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void databaseRejectsLedgerUpdateAndDelete() throws Exception {
        signupAndLogin("member@example.com", "seed_member");
        UUID ledgerId = jdbc.queryForObject(
                "SELECT id FROM point_ledgers WHERE source_type='SIGNUP_BONUS'", UUID.class);

        assertThatThrownBy(() -> jdbc.update("UPDATE point_ledgers SET paid_amount=0 WHERE id=?", ledgerId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM point_ledgers WHERE id=?", ledgerId))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM point_ledgers WHERE id=?", Integer.class, ledgerId))
                .isEqualTo(300);
    }

    @Test
    void publishesWalletAndLedgerOpenApiContracts() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("paths");

        assertThat(paths.has("/api/v1/me/wallet")).isTrue();
        assertThat(paths.has("/api/v1/me/point-ledgers")).isTrue();
        assertThat(paths.get("/api/v1/me/wallet").get("get").get("security").toString())
                .contains("bearerAuth");
        assertThat(paths.get("/api/v1/me/point-ledgers").get("get").get("responses").has("400")).isTrue();
        assertThat(paths.get("/api/v1/me/point-ledgers").get("get").get("responses").has("401")).isTrue();
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","profileId":"%s"}
                                """.formatted(email, profileId)))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
    }

    private void insertLedger(UUID userId, UUID id, Instant createdAt, int balanceAfter) {
        jdbc.update("""
                INSERT INTO point_ledgers
                    (id, user_id, type, original_amount, paid_amount, expired_amount,
                     balance_after, source_type, source_id, created_at)
                VALUES (?, ?, 'CREDIT', 1, 1, 0, ?, 'SIGNUP_BONUS', ?, ?)
                """, id, userId, balanceAfter, UUID.randomUUID(), Timestamp.from(createdAt));
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(authenticatedGet(path, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedGet(
            String path, String token) {
        return get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
