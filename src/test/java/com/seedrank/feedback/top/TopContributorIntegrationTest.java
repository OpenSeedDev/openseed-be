package com.seedrank.feedback.top;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TopContributorIntegrationTest.FixedClockConfig.class})
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class TopContributorIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-26T01:00:00Z");
    private static final String CONTENT = "기여자 순위 테스트를 위한 충분히 긴 구조화 피드백 내용입니다. "
            + "문제와 해결 방향을 구체적으로 설명하며 데이터베이스 길이 제약도 만족하도록 작성합니다. "
            + "대상 사용자의 상황과 예상 효과, 검증할 수 있는 근거를 함께 적어 실제 피드백과 같은 길이를 보장합니다.";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM feedback_revisions");
        jdbc.update("DELETE FROM contributions");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void returnsAtMostFiveContributorsWithCurrentProfileAndPrimaryCategory() throws Exception {
        for (int userIndex = 1; userIndex <= 6; userIndex++) {
            UUID userId = user("profile_" + userIndex);
            for (int count = 0; count < 7 - userIndex; count++) {
                contribute(userId, count < 4 ? "TECH" : "SERVICE", NOW.minusSeconds(3_600L * count));
            }
        }
        jdbc.update("UPDATE users SET profile_id = 'current_profile' WHERE profile_id = 'profile_1'");

        JsonNode response = response();

        assertThat(response).hasSize(5);
        assertThat(response.get(0).path("profileId").asText()).isEqualTo("current_profile");
        assertThat(response.get(0).path("totalContributionCount").asLong()).isEqualTo(6);
        assertThat(response.get(0).path("recent30DayContributionCount").asLong()).isEqualTo(6);
        assertThat(response.get(0).path("primaryCategory").asText()).isEqualTo("TECH");
        assertThat(response.get(4).path("profileId").asText()).isEqualTo("profile_5");
    }

    @Test
    void breaksTiesByRecentThirtyDayCountThenLastContributionTime() throws Exception {
        UUID moreRecentCount = user("more_recent_count");
        UUID newerLastContribution = user("newer_last");
        UUID olderLastContribution = user("older_last");

        contribute(moreRecentCount, "TECH", NOW.minusSeconds(60));
        contribute(moreRecentCount, "TECH", NOW.minusSeconds(29L * 86_400));
        contribute(moreRecentCount, "TECH", NOW.minusSeconds(31L * 86_400));

        contribute(newerLastContribution, "TECH", NOW.minusSeconds(120));
        contribute(newerLastContribution, "TECH", NOW.minusSeconds(31L * 86_400));
        contribute(newerLastContribution, "TECH", NOW.minusSeconds(32L * 86_400));

        contribute(olderLastContribution, "TECH", NOW.minusSeconds(180));
        contribute(olderLastContribution, "TECH", NOW.minusSeconds(31L * 86_400));
        contribute(olderLastContribution, "TECH", NOW.minusSeconds(32L * 86_400));

        JsonNode response = response();

        assertThat(response.get(0).path("profileId").asText()).isEqualTo("more_recent_count");
        assertThat(response.get(0).path("recent30DayContributionCount").asLong()).isEqualTo(2);
        assertThat(response.get(1).path("profileId").asText()).isEqualTo("newer_last");
        assertThat(response.get(2).path("profileId").asText()).isEqualTo("older_last");
    }

    @Test
    void includesThirtyDayBoundaryAndUsesStableCategoryTieBreak() throws Exception {
        UUID userId = user("boundary_user");
        contribute(userId, "TECH", NOW.minusSeconds(30L * 86_400));
        contribute(userId, "SERVICE", NOW.minusSeconds(31L * 86_400));

        JsonNode response = response();

        assertThat(response.get(0).path("recent30DayContributionCount").asLong()).isEqualTo(1);
        assertThat(response.get(0).path("primaryCategory").asText()).isEqualTo("SERVICE");
    }

    @Test
    void returnsEmptyAnonymousResponseAndPublishesOpenApiContract() throws Exception {
        mockMvc.perform(get("/api/v1/contributors/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        JsonNode operation = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("paths").path("/api/v1/contributors/top").path("get");

        assertThat(operation.has("security")).isFalse();
        assertThat(operation.path("responses").has("200")).isTrue();
    }

    private JsonNode response() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/contributors/top"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private UUID user(String profileId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id, email, password_hash, profile_id, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'USER', 'ACTIVE', ?, ?)
                """, id, id + "@example.com", profileId, Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private void contribute(UUID userId, String category, Instant createdAt) {
        UUID ideaId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ideas(id, author_id, status, title, category, summary, problem,
                                  target_customer, solution, business_model, visibility,
                                  current_unit_price, published_at, created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', 'title', ?, 'summary', 'problem', 'customer',
                        'solution', 'model', 'PUBLIC', 10, ?, ?, ?)
                """, ideaId, userId, category, Timestamp.from(createdAt), Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO feedbacks(
                    id, idea_id, user_id, feedback_type, content, accepted_at, created_at)
                VALUES (?, ?, ?, 'OTHER', ?, ?, ?)
                """, feedbackId, ideaId, userId, CONTENT, Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO contributions(id, idea_id, user_id, source_feedback_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ideaId, userId, feedbackId, Timestamp.from(createdAt));
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
