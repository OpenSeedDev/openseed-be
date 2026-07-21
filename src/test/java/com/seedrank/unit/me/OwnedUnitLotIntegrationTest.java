package com.seedrank.unit.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@Import({TestcontainersConfiguration.class, OwnedUnitLotIntegrationTest.FixedClockConfig.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class OwnedUnitLotIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");

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
    void returnsOnlyOwnedActiveLotsWithValuationUnlockAndIdeaTotal() throws Exception {
        Session owner = signupAndLogin("owner@example.com", "lot_owner");
        Session other = signupAndLogin("other@example.com", "lot_other");
        UUID ideaId = insertPublishedIdea(other.userId(), "공유 이동 서비스", 15);
        UUID first = insertLot(ideaId, owner.userId(), 2, 10, NOW.minusSeconds(86_400), "LOCKED");
        insertLot(ideaId, owner.userId(), 3, 12, NOW.minusSeconds(3_600), "LOCKED");
        insertLot(ideaId, owner.userId(), 7, 10, NOW.minusSeconds(172_800), "RECOVERED");
        insertLot(ideaId, other.userId(), 9, 10, NOW.minusSeconds(1_800), "LOCKED");

        mockMvc.perform(get("/api/v1/me/unit-lots").header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].units").value(3))
                .andExpect(jsonPath("$.items[0].activeUnitsForIdea").value(5))
                .andExpect(jsonPath("$.items[0].recoveryAvailable").value(false))
                .andExpect(jsonPath("$.items[1].lotId").value(first.toString()))
                .andExpect(jsonPath("$.items[1].ideaId").value(ideaId.toString()))
                .andExpect(jsonPath("$.items[1].ideaTitle").value("공유 이동 서비스"))
                .andExpect(jsonPath("$.items[1].purchasePrice").value(10))
                .andExpect(jsonPath("$.items[1].principal").value(20))
                .andExpect(jsonPath("$.items[1].currentUnitPrice").value(15))
                .andExpect(jsonPath("$.items[1].currentValue").value(30))
                .andExpect(jsonPath("$.items[1].pointDifference").value(10))
                .andExpect(jsonPath("$.items[1].activeUnitsForIdea").value(5))
                .andExpect(jsonPath("$.items[1].recoveryAvailable").value(true))
                .andExpect(jsonPath("$.nonMonetaryNotice").exists());
    }

    @Test
    void cursorHasNoDuplicateOrGapWhenPurchaseTimesAreEqual() throws Exception {
        Session owner = signupAndLogin("cursor@example.com", "lot_cursor");
        Session author = signupAndLogin("author@example.com", "lot_author");
        UUID ideaId = insertPublishedIdea(author.userId(), "Cursor 아이디어", 10);
        Instant purchasedAt = NOW.minusSeconds(600);
        var expected = new HashSet<UUID>();
        for (int index = 0; index < 3; index++) {
            expected.add(insertLot(ideaId, owner.userId(), 1, 10, purchasedAt, "LOCKED"));
        }

        String firstBody = mockMvc.perform(get("/api/v1/me/unit-lots?size=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        var firstJson = objectMapper.readTree(firstBody);
        String cursor = firstJson.get("nextCursor").asText();
        String secondBody = mockMvc.perform(get("/api/v1/me/unit-lots?size=2&cursor={cursor}", cursor)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn().getResponse().getContentAsString();

        var actual = new HashSet<UUID>();
        firstJson.get("items").forEach(item -> actual.add(UUID.fromString(item.get("lotId").asText())));
        objectMapper.readTree(secondBody).get("items")
                .forEach(item -> actual.add(UUID.fromString(item.get("lotId").asText())));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void emptyPortfolioAndRequestValidationAreStable() throws Exception {
        Session owner = signupAndLogin("empty@example.com", "lot_empty");
        mockMvc.perform(get("/api/v1/me/unit-lots").header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(get("/api/v1/me/unit-lots")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/unit-lots?cursor=invalid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/unit-lots?size=0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/unit-lots?size=101")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiPublishesOwnedLotContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/me/unit-lots'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/unit-lots'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/unit-lots'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/unit-lots'].get.responses['401']").exists());
    }

    private UUID insertPublishedIdea(UUID authorId, String title, int price) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ideas(id, author_id, status, title, category, summary, problem, target_customer,
                                  solution, business_model, visibility, current_unit_price, published_at,
                                  created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', ?, 'SERVICE', '요약', '문제', '고객', '해결', '모델',
                        'PUBLIC', ?, ?, ?, ?)
                """, id, authorId, title, price,
                Timestamp.from(NOW.minusSeconds(172_800)), Timestamp.from(NOW.minusSeconds(172_800)),
                Timestamp.from(NOW.minusSeconds(172_800)));
        return id;
    }

    private UUID insertLot(UUID ideaId, UUID userId, int units, int price, Instant purchasedAt, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO seed_unit_lots(id, idea_id, user_id, units, purchase_price, principal,
                                           purchase_request_key, purchased_at, unlocked_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, ideaId, userId, units, price, units * price, "fixture-" + id,
                Timestamp.from(purchasedAt), Timestamp.from(purchasedAt.plusSeconds(86_400)), status);
        return id;
    }

    private Session signupAndLogin(String email, String profileId) throws Exception {
        String signupBody = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(signupBody).get("userId").asText());
        String loginBody = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Session(userId, objectMapper.readTree(loginBody).get("accessToken").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(UUID userId, String token) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
