package com.seedrank.idea.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
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
class MyIdeaListIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @AfterEach
    void removeRowsForExistingTestFixtures() {
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void listsOnlyTheAuthorsIdeasNewestFirstWithCardFields() throws Exception {
        String authorToken = signupAndLogin("author@example.com", "idea_author");
        signupAndLogin("other@example.com", "idea_other");
        UUID authorId = userId("author@example.com");
        UUID otherId = userId("other@example.com");
        UUID older = insertIdea(authorId, "DRAFT", "오래된 아이디어", Instant.parse("2026-07-20T01:00:00Z"));
        UUID newer = insertIdea(authorId, "DRAFT", "최신 아이디어", Instant.parse("2026-07-20T02:00:00Z"));
        insertIdea(otherId, "DRAFT", "다른 사람 아이디어", Instant.parse("2026-07-20T03:00:00Z"));

        JsonNode response = getIdeas("/api/v1/me/ideas", authorToken);

        assertThat(response.get("items").size()).isEqualTo(2);
        assertThat(response.at("/items/0/id").asText()).isEqualTo(newer.toString());
        assertThat(response.at("/items/1/id").asText()).isEqualTo(older.toString());
        assertThat(response.at("/items/0").fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "status", "title", "category", "summary", "createdAt", "updatedAt");
        assertThat(response.toString()).doesNotContain(
                "다른 사람 아이디어", "problem", "targetCustomer", "solution", "businessModel",
                "authorId", "email", "validationQuestions");
        assertThat(response.get("nextCursor").isNull()).isTrue();
        assertThat(response.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void filtersDraftPublishedAndArchivedOrReturnsAllStatuses() throws Exception {
        String token = signupAndLogin("author@example.com", "idea_author");
        UUID authorId = userId("author@example.com");
        insertIdea(authorId, "DRAFT", "초안", Instant.parse("2026-07-20T01:00:00Z"));
        insertIdea(authorId, "PUBLISHED", "게시", Instant.parse("2026-07-20T02:00:00Z"));
        insertIdea(authorId, "ARCHIVED", "보관", Instant.parse("2026-07-20T03:00:00Z"));

        assertThat(statuses(getIdeas("/api/v1/me/ideas", token)))
                .containsExactlyInAnyOrder("DRAFT", "PUBLISHED", "ARCHIVED");
        assertThat(statuses(getIdeas("/api/v1/me/ideas?status=DRAFT", token))).containsExactly("DRAFT");
        assertThat(statuses(getIdeas("/api/v1/me/ideas?status=PUBLISHED", token))).containsExactly("PUBLISHED");
        assertThat(statuses(getIdeas("/api/v1/me/ideas?status=ARCHIVED", token))).containsExactly("ARCHIVED");
    }

    @Test
    void cursorPaginationHasNoDuplicatesOrGapsIncludingEqualUpdateTimes() throws Exception {
        String token = signupAndLogin("author@example.com", "idea_author");
        UUID authorId = userId("author@example.com");
        Instant sameTime = Instant.parse("2026-07-20T02:00:00Z");
        Set<UUID> expected = Set.of(
                insertIdea(authorId, "DRAFT", "첫째", sameTime),
                insertIdea(authorId, "DRAFT", "둘째", sameTime),
                insertIdea(authorId, "DRAFT", "셋째", Instant.parse("2026-07-20T01:00:00Z")));

        JsonNode first = getIdeas("/api/v1/me/ideas?size=2", token);
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        String cursor = first.get("nextCursor").asText();

        JsonNode second = getIdeas("/api/v1/me/ideas?size=2&cursor=" + cursor, token);
        assertThat(second.get("items").size()).isEqualTo(1);
        assertThat(second.get("hasNext").asBoolean()).isFalse();
        assertThat(second.get("nextCursor").isNull()).isTrue();

        Set<UUID> actual = Set.of(
                UUID.fromString(first.at("/items/0/id").asText()),
                UUID.fromString(first.at("/items/1/id").asText()),
                UUID.fromString(second.at("/items/0/id").asText()));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void returnsAnEmptyPageWhenTheAuthorHasNoIdeas() throws Exception {
        String token = signupAndLogin("author@example.com", "idea_author");

        mockMvc.perform(get("/api/v1/me/ideas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void rejectsInvalidStatusCursorAndPageSizeAndRequiresAuthentication() throws Exception {
        String token = signupAndLogin("author@example.com", "idea_author");
        for (String query : Set.of("status=UNKNOWN", "cursor=invalid", "size=0", "size=101", "size=text")) {
            mockMvc.perform(get("/api/v1/me/ideas?" + query)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        mockMvc.perform(get("/api/v1/me/ideas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void publishesTheMyIdeaListOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/me/ideas'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/ideas'].get.parameters[?(@.name == 'status')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/ideas'].get.parameters[?(@.name == 'cursor')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/ideas'].get.parameters[?(@.name == 'size')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/ideas'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/ideas'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/ideas'].get.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.MyIdeaItemResponse.properties.problem").doesNotExist());
    }

    private JsonNode getIdeas(String path, String token) throws Exception {
        String body = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private java.util.List<String> statuses(JsonNode response) {
        return response.get("items").valueStream().map(item -> item.get("status").asText()).toList();
    }

    private UUID insertIdea(UUID authorId, String status, String title, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ideas (
                    id, author_id, status, title, category, summary, problem,
                    target_customer, solution, business_model, visibility, current_unit_price,
                    published_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ETC', ?, '문제', NULL, NULL, NULL, ?, ?, ?, ?, ?)
                """, id, authorId, status, title, title + " 요약",
                "DRAFT".equals(status) ? null : "PUBLIC",
                "DRAFT".equals(status) ? null : 10,
                "DRAFT".equals(status) ? null : Timestamp.from(updatedAt.minusSeconds(30)),
                Timestamp.from(updatedAt.minusSeconds(60)), Timestamp.from(updatedAt));
        return id;
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
    }
}
