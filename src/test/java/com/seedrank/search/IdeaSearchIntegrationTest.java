package com.seedrank.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class IdeaSearchIntegrationTest {

    private static final Instant CALCULATED_AT = Instant.parse("2026-07-26T01:00:00Z");
    private static final Set<String> CARD_FIELDS = Set.of(
            "rank", "rankChange", "title", "summary", "category", "companyInterestCount", "likeCount");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM ranking_current");
        jdbc.update("DELETE FROM ranking_runs");
        jdbc.update("DELETE FROM idea_keywords");
        jdbc.update("DELETE FROM idea_likes");
        jdbc.update("DELETE FROM company_interests");
        jdbc.update("DELETE FROM feedback_revisions");
        jdbc.update("DELETE FROM contributions");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM idea_view_events");
        jdbc.update("DELETE FROM idea_metric_hourly");
        jdbc.update("DELETE FROM idea_metric_current");
        jdbc.update("DELETE FROM seed_unit_recoveries");
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM company_verifications");
        jdbc.update("DELETE FROM company_profiles");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void searchesTitleAndKeywordCaseInsensitivelyInRankingOrderWithoutDuplicateCards() throws Exception {
        UUID keywordMatch = idea("PUBLIC", "무관한 제목", "첫 요약", "SERVICE", "PUBLISHED");
        UUID titleAndKeywordMatch = idea("MATCHING", "Green Mobility", "둘 요약", "TECH", "PUBLISHED");
        UUID semiPublicMatch = idea("SEMI_PUBLIC", "또 다른 제목", "셋 요약", "FOOD", "PUBLISHED");
        rankingRun();
        ranking(keywordMatch, 1, 2, 4, 7);
        ranking(titleAndKeywordMatch, 2, 2, 3, 5);
        ranking(semiPublicMatch, 3, null, 2, 1);
        keyword(keywordMatch, "GREEN energy");
        keyword(titleAndKeywordMatch, "green");
        keyword(titleAndKeywordMatch, "green-tech");
        keyword(semiPublicMatch, "Green life");

        JsonNode response = body(get("/api/v1/search").queryParam("q", "  gReEn  "));

        assertThat(response).hasSize(3);
        assertThat(response.get(0).path("title").asText()).isEqualTo("무관한 제목");
        assertThat(response.get(1).path("title").asText()).isEqualTo("Green Mobility");
        assertThat(response.get(2).path("title").asText()).isEqualTo("또 다른 제목");
        response.forEach(card -> assertThat(card.fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(CARD_FIELDS));
        assertThat(response.get(0).path("rankChange").asInt()).isEqualTo(1);
        assertThat(response.get(2).path("rankChange").isNull()).isTrue();
    }

    @Test
    void treatsSqlWildcardCharactersAsLiteralSearchText() throws Exception {
        UUID literal = idea("PUBLIC", "100% 절감_안", "요약", "SERVICE", "PUBLISHED");
        UUID wildcardOnly = idea("PUBLIC", "1000 절감X안", "요약", "SERVICE", "PUBLISHED");
        rankingRun();
        ranking(literal, 1, 1, 0, 0);
        ranking(wildcardOnly, 2, 2, 0, 0);

        JsonNode percent = body(get("/api/v1/search").queryParam("q", "%"));
        JsonNode underscore = body(get("/api/v1/search").queryParam("q", "_"));

        assertThat(percent).hasSize(1);
        assertThat(percent.get(0).path("title").asText()).isEqualTo("100% 절감_안");
        assertThat(underscore).hasSize(1);
        assertThat(underscore.get(0).path("title").asText()).isEqualTo("100% 절감_안");
    }

    @Test
    void excludesUnpublishedArchivedAndUnrankedIdeasAcrossAllVisibilities() throws Exception {
        UUID ranked = idea("PUBLIC", "찾을 제목", "요약", "SERVICE", "PUBLISHED");
        UUID archived = idea("MATCHING", "찾을 보관", "요약", "SERVICE", "ARCHIVED");
        idea("SEMI_PUBLIC", "찾을 미랭킹", "요약", "SERVICE", "PUBLISHED");
        rankingRun();
        ranking(ranked, 1, 1, 0, 0);
        ranking(archived, 2, 2, 0, 0);

        JsonNode response = body(get("/api/v1/search").queryParam("q", "찾을"));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).path("title").asText()).isEqualTo("찾을 제목");
    }

    @Test
    void rejectsBlankMissingAndLongerThanTwentyCharacterQueries() throws Exception {
        mockMvc.perform(get("/api/v1/search").queryParam("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search").queryParam("q", "123456789012345678901"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void publishesAnonymousSearchOpenApiContract() throws Exception {
        JsonNode operation = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("paths").path("/api/v1/search").path("get");

        assertThat(operation.has("security")).isFalse();
        JsonNode query = java.util.stream.StreamSupport.stream(
                        operation.path("parameters").spliterator(), false)
                .filter(parameter -> parameter.path("name").asText().equals("q"))
                .findFirst()
                .orElseThrow();
        assertThat(query.path("required").asBoolean()).isTrue();
        assertThat(query.path("schema").path("maxLength").asInt()).isEqualTo(20);
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("400")).isTrue();
    }

    private JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private UUID idea(String visibility, String title, String summary, String category, String status) {
        UUID authorId = user();
        UUID ideaId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ideas(id, author_id, status, title, category, summary, problem,
                                  target_customer, solution, business_model, visibility,
                                  current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'problem', 'customer', 'solution', 'model', ?, 10, ?, ?, ?)
                """, ideaId, authorId, status, title, category, summary, visibility,
                Timestamp.from(CALCULATED_AT.minusSeconds(86_400)),
                Timestamp.from(CALCULATED_AT.minusSeconds(86_400)),
                Timestamp.from(CALCULATED_AT.minusSeconds(86_400)));
        return ideaId;
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id, email, password_hash, profile_id, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'USER', 'ACTIVE', ?, ?)
                """, id, id + "@example.com", "user_" + id.toString().substring(0, 8),
                Timestamp.from(CALCULATED_AT.minusSeconds(100_000)),
                Timestamp.from(CALCULATED_AT.minusSeconds(100_000)));
        return id;
    }

    private void rankingRun() {
        jdbc.update("INSERT INTO ranking_runs(target_hour, idea_count, published_at) VALUES (?, 4, ?)",
                Timestamp.from(CALCULATED_AT), Timestamp.from(CALCULATED_AT));
    }

    private void ranking(UUID ideaId, int rank, Integer previousRank, int companyCount, int likeCount) {
        jdbc.update("""
                INSERT INTO ranking_current(
                    idea_id, rank_position, previous_rank_position, total_score, components, calculated_at)
                VALUES (?, ?, ?, 1, jsonb_build_object(
                    'companyInterestCount', ?::integer,
                    'likeCount', ?::integer
                ), ?)
                """, ideaId, rank, previousRank, companyCount, likeCount, Timestamp.from(CALCULATED_AT));
    }

    private void keyword(UUID ideaId, String value) {
        jdbc.update("INSERT INTO idea_keywords(id, idea_id, keyword) VALUES (?, ?, ?)",
                UUID.randomUUID(), ideaId, value);
    }
}
