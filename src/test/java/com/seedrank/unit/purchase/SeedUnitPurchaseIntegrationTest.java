package com.seedrank.unit.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.sql.Timestamp;
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
class SeedUnitPurchaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.update("DELETE FROM idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @AfterEach
    void removeNewRowsForExistingFixtures() {
        jdbc.update("DELETE FROM seed_unit_lots");
    }

    @Test
    void previewsCurrentPriceTotalExpectedBalanceLockAndNonMonetaryNotice() throws Exception {
        String author = signupAndLogin("author@example.com", "unit_author");
        String buyer = signupAndLogin("buyer@example.com", "unit_buyer");
        String ideaId = publishedIdea(author);

        preview(buyer, ideaId, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ideaId").value(ideaId))
                .andExpect(jsonPath("$.units").value(3))
                .andExpect(jsonPath("$.unitPrice").value(10))
                .andExpect(jsonPath("$.totalPoint").value(30))
                .andExpect(jsonPath("$.expectedBalance").value(300))
                .andExpect(jsonPath("$.lockedForHours").value(24))
                .andExpect(jsonPath("$.nonMonetaryNotice").isNotEmpty());
    }

    @Test
    void purchasesWithAtomicDebitLedgerAndLockedLot() throws Exception {
        String author = signupAndLogin("purchase-author@example.com", "purchase_author");
        String buyer = signupAndLogin("purchase-buyer@example.com", "purchase_buyer");
        String ideaId = publishedIdea(author);

        String body = purchase(buyer, ideaId, 4, 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ideaId").value(ideaId))
                .andExpect(jsonPath("$.units").value(4))
                .andExpect(jsonPath("$.purchasePrice").value(10))
                .andExpect(jsonPath("$.principal").value(40))
                .andExpect(jsonPath("$.balanceAfter").value(290))
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andReturn().getResponse().getContentAsString();

        UUID lotId = UUID.fromString(objectMapper.readTree(body).get("lotId").asText());
        var lot = jdbc.queryForMap("SELECT * FROM seed_unit_lots WHERE id=?", lotId);
        assertThat(lot).containsEntry("units", 4).containsEntry("purchase_price", 10)
                .containsEntry("principal", 40).containsEntry("status", "LOCKED");
        var purchasedAt = ((Timestamp) lot.get("purchased_at")).toInstant();
        var unlockedAt = ((Timestamp) lot.get("unlocked_at")).toInstant();
        assertThat(Duration.between(purchasedAt, unlockedAt)).isEqualTo(Duration.ofHours(24));

        var ledger = jdbc.queryForMap("SELECT * FROM point_ledgers WHERE source_id=?", lotId);
        assertThat(ledger).containsEntry("type", "DEBIT")
                .containsEntry("original_amount", 40)
                .containsEntry("paid_amount", 40)
                .containsEntry("expired_amount", 0)
                .containsEntry("balance_after", 290)
                .containsEntry("source_type", "UNIT_PURCHASE");
    }

    @Test
    void rejectsNonPositiveUnitsAndInvalidAuthentication() throws Exception {
        String author = signupAndLogin("validation-author@example.com", "validation_author");
        String ideaId = publishedIdea(author);

        preview(null, ideaId, 1).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        preview(author, ideaId, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        purchase(author, ideaId, -1, 10).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void hidesDraftAndMissingIdeas() throws Exception {
        String author = signupAndLogin("draft-author@example.com", "draft_author");
        String buyer = signupAndLogin("draft-buyer@example.com", "draft_buyer");
        String draftId = createDraft(author);

        preview(buyer, draftId, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
        purchase(buyer, UUID.randomUUID().toString(), 1, 10).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void rejectsSelfPurchaseWithoutSideEffects() throws Exception {
        String author = signupAndLogin("self@example.com", "self_author");
        String ideaId = publishedIdea(author);

        purchase(author, ideaId, 1, 10).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_UNIT_PURCHASE"));
        assertNoPurchaseSideEffects(author, ideaId);
    }

    @Test
    void rejectsChangedPriceWithoutSideEffects() throws Exception {
        String author = signupAndLogin("price-author@example.com", "price_author");
        String buyer = signupAndLogin("price-buyer@example.com", "price_buyer");
        String ideaId = publishedIdea(author);

        purchase(buyer, ideaId, 2, 9).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
        assertNoPurchaseSideEffects(buyer, ideaId);
    }

    @Test
    void rejectsInsufficientPointWithoutSideEffects() throws Exception {
        String author = signupAndLogin("poor-author@example.com", "poor_author");
        String buyer = signupAndLogin("poor-buyer@example.com", "poor_buyer");
        String ideaId = publishedIdea(author);
        setBalance(buyer, 9);

        purchase(buyer, ideaId, 1, 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"));
        assertNoPurchaseSideEffects(buyer, ideaId);
    }

    @Test
    void enforcesSingleDailyAndIdeaActivePrincipalLimits() throws Exception {
        String author = signupAndLogin("limits-author@example.com", "limits_author");
        String buyer = signupAndLogin("limits-buyer@example.com", "limits_buyer");
        String firstIdea = publishedIdea(author);

        purchase(buyer, firstIdea, 11, 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_LIMIT_EXCEEDED"));

        for (int attempt = 0; attempt < 3; attempt++) {
            purchase(buyer, firstIdea, 10, 10).andExpect(status().isCreated());
        }
        setBalance(buyer, 100);
        purchase(buyer, firstIdea, 1, 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_LIMIT_EXCEEDED"));

        String secondIdea = publishedIdea(author);
        purchase(buyer, secondIdea, 1, 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_LIMIT_EXCEEDED"));
    }

    @Test
    void publishesBothOpenApiContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/unit-purchase-preview'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/unit-purchases'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/unit-purchases'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}/unit-purchases'].post.responses['409']").exists());
    }

    private void assertNoPurchaseSideEffects(String token, String ideaId) throws Exception {
        UUID userId = userId(token);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seed_unit_lots WHERE user_id=? AND idea_id=?",
                Integer.class, userId, UUID.fromString(ideaId))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledgers WHERE user_id=? AND source_type='UNIT_PURCHASE'",
                Integer.class, userId)).isZero();
    }

    private ResultActions preview(String token, String ideaId, int units) throws Exception {
        var request = post("/api/v1/ideas/{ideaId}/unit-purchase-preview", ideaId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"units\":%d}".formatted(units));
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mockMvc.perform(request);
    }

    private ResultActions purchase(String token, String ideaId, int units, int confirmedPrice) throws Exception {
        var request = post("/api/v1/ideas/{ideaId}/unit-purchases", ideaId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("{\"units\":%d,\"confirmedUnitPrice\":%d}".formatted(units, confirmedPrice));
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mockMvc.perform(request);
    }

    private String publishedIdea(String authorToken) throws Exception {
        String ideaId = createDraft(authorToken);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/ideas/{ideaId}/validation-questions", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[\"실제로 필요한가요?\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ideas/{ideaId}/publish", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk());
        return ideaId;
    }

    private String createDraft(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Unit 대상 아이디어","category":"SERVICE","summary":"요약",
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

    private void setBalance(String token, int balance) throws Exception {
        jdbc.update("UPDATE point_wallets SET balance=? WHERE user_id=?", balance, userId(token));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
