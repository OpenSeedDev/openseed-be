package com.seedrank.ranking.main;

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
class RankingMainIntegrationTest {

    private static final Instant CALCULATED_AT = Instant.parse("2026-07-22T07:00:00Z");
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
        jdbc.update("DELETE FROM idea_likes");
        jdbc.update("DELETE FROM feedback_revisions");
        jdbc.update("DELETE FROM contributions");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM idea_view_events");
        jdbc.update("DELETE FROM idea_metric_hourly");
        jdbc.update("DELETE FROM idea_metric_current");
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void returnsOnlySevenCardFieldsInCurrentRankingOrder() throws Exception {
        UUID publicIdea = idea("PUBLIC", "공개 아이디어", "공개 요약", "SERVICE", "PUBLISHED");
        UUID matchingIdea = idea("MATCHING", "매칭 아이디어", "매칭 요약", "TECH", "PUBLISHED");
        UUID semiPublicIdea = idea("SEMI_PUBLIC", "반공개 아이디어", "반공개 요약", "FOOD", "PUBLISHED");
        rankingRun();
        ranking(matchingIdea, 2, 1, 3, 7);
        ranking(publicIdea, 1, 3, 2, 5);
        ranking(semiPublicIdea, 3, null, 4, 9);

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(response).hasSize(3);
        assertThat(response.get(0).fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(CARD_FIELDS);
        assertThat(response.get(1).fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(CARD_FIELDS);
        assertThat(response.get(2).fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(CARD_FIELDS);
        assertThat(response.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(response.get(0).get("rankChange").asInt()).isEqualTo(2);
        assertThat(response.get(0).get("title").asText()).isEqualTo("공개 아이디어");
        assertThat(response.get(0).get("summary").asText()).isEqualTo("공개 요약");
        assertThat(response.get(0).get("category").asText()).isEqualTo("SERVICE");
        assertThat(response.get(0).get("companyInterestCount").asInt()).isEqualTo(2);
        assertThat(response.get(0).get("likeCount").asInt()).isEqualTo(5);
        assertThat(response.get(1).get("rankChange").asInt()).isEqualTo(-1);
        assertThat(response.get(2).get("rankChange").isNull()).isTrue();
    }

    @Test
    void ignoresVisibilityButExcludesIdeasOutsidePublishedCurrentRanking() throws Exception {
        UUID publicIdea = idea("PUBLIC", "public", "summary", "SERVICE", "PUBLISHED");
        UUID matchingIdea = idea("MATCHING", "matching", "summary", "SERVICE", "PUBLISHED");
        UUID semiPublicIdea = idea("SEMI_PUBLIC", "semi", "summary", "SERVICE", "PUBLISHED");
        UUID archivedIdea = idea("PUBLIC", "archived", "summary", "SERVICE", "ARCHIVED");
        idea("PUBLIC", "unranked", "summary", "SERVICE", "PUBLISHED");
        rankingRun();
        ranking(publicIdea, 1, 1, 0, 0);
        ranking(matchingIdea, 2, 2, 0, 0);
        ranking(semiPublicIdea, 3, 3, 0, 0);
        ranking(archivedIdea, 4, 4, 0, 0);

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(java.util.stream.StreamSupport.stream(response.spliterator(), false)
                .map(item -> item.get("title").asText()).toList())
                .containsExactly("public", "matching", "semi");
    }

    @Test
    void returnsAnEmptyListBeforeTheFirstRankingPublication() throws Exception {
        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void publishesAnonymousRankingOpenApiContractWithoutFilters() throws Exception {
        JsonNode operation = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("paths").path("/api/v1/rankings").path("get");

        assertThat(operation.has("security")).isFalse();
        assertThat(java.util.stream.StreamSupport.stream(operation.path("parameters").spliterator(), false)
                .map(parameter -> parameter.path("in").asText()).toList()).doesNotContain("query");
        assertThat(operation.path("responses").has("200")).isTrue();
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
}
