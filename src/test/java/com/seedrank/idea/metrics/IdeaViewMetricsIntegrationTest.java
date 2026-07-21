package com.seedrank.idea.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, IdeaViewMetricsIntegrationTest.ClockTestConfiguration.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class IdeaViewMetricsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM idea_view_events");
        jdbc.update("DELETE FROM idea_metric_hourly");
        jdbc.update("DELETE FROM idea_metric_current");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
        clock.set(Instant.parse("2026-07-21T10:15:00Z"));
    }

    @AfterEach
    void removeMetricAndIdeaRowsForExistingTestFixtures() {
        jdbc.update("DELETE FROM idea_view_events");
        jdbc.update("DELETE FROM idea_metric_hourly");
        jdbc.update("DELETE FROM idea_metric_current");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
    }

    @Test
    void guestDetailCountsOncePerServerSessionAndUtcHour() throws Exception {
        UUID ideaId = publishedIdea("guest-author@example.com", "guest_author", "PUBLIC");
        MockHttpSession guest = new MockHttpSession();

        read(ideaId, null, guest).andExpect(jsonPath("$.viewCount").value(1));
        read(ideaId, null, guest).andExpect(jsonPath("$.viewCount").value(1));

        assertThat(currentCount(ideaId)).isEqualTo(1L);
        assertThat(hourlyDeltas(ideaId)).containsExactly(1);
        assertThat(eventCount(ideaId)).isEqualTo(1L);
    }

    @Test
    void authenticatedUserCountsOnceAcrossMultipleLoginSessions() throws Exception {
        UUID ideaId = publishedIdea("target-author@example.com", "target_author", "PUBLIC");
        signup("viewer@example.com", "metric_viewer");
        String firstLogin = login("viewer@example.com");
        String secondLogin = login("viewer@example.com");

        read(ideaId, firstLogin, null).andExpect(jsonPath("$.viewCount").value(1));
        read(ideaId, secondLogin, null).andExpect(jsonPath("$.viewCount").value(1));

        assertThat(currentCount(ideaId)).isEqualTo(1L);
        assertThat(eventCount(ideaId)).isEqualTo(1L);
    }

    @Test
    void nextUtcHourCreatesANewEventAndHourlyBucket() throws Exception {
        UUID ideaId = publishedIdea("hour-author@example.com", "hour_author", "PUBLIC");
        MockHttpSession guest = new MockHttpSession();

        read(ideaId, null, guest).andExpect(jsonPath("$.viewCount").value(1));
        clock.set(Instant.parse("2026-07-21T11:00:00Z"));
        read(ideaId, null, guest).andExpect(jsonPath("$.viewCount").value(2));

        assertThat(hourlyDeltas(ideaId)).containsExactly(1, 1);
        assertThat(jdbc.queryForList(
                "SELECT bucket_hour FROM idea_metric_hourly WHERE idea_id=? ORDER BY bucket_hour",
                Instant.class, ideaId))
                .containsExactly(Instant.parse("2026-07-21T10:00:00Z"), Instant.parse("2026-07-21T11:00:00Z"));
    }

    @Test
    void everyAllowedPublishedVisibilityReturnsTheCommonViewCount() throws Exception {
        for (String visibility : List.of("PUBLIC", "SEMI_PUBLIC", "MATCHING")) {
            String suffix = visibility.toLowerCase().replace("_", "");
            UUID ideaId = publishedIdea(suffix + "@example.com", "metrics_" + suffix, visibility);
            read(ideaId, null, new MockHttpSession()).andExpect(jsonPath("$.viewCount").value(1));
        }
    }

    @Test
    void rejectedOrDraftDetailDoesNotCreateMetricRows() throws Exception {
        String author = signupAndLogin("draft-author@example.com", "draft_author");
        UUID draftId = draftIdea(author);

        mockMvc.perform(get("/api/v1/ideas/{ideaId}", draftId))
                .andExpect(status().isNotFound());
        read(draftId, author, null).andExpect(jsonPath("$.viewCount").value(0));
        mockMvc.perform(get("/api/v1/ideas/{ideaId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ideas/{ideaId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_view_events", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_metric_current", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idea_metric_hourly", Long.class)).isZero();
    }

    @Test
    void concurrentDuplicateAndDistinctViewsNeverLoseOrDoubleCount() throws Exception {
        UUID ideaId = publishedIdea("concurrent-author@example.com", "concurrent_author", "PUBLIC");
        signup("same-viewer@example.com", "same_viewer");
        String duplicateToken = login("same-viewer@example.com");

        List<String> distinctTokens = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            String email = "distinct" + index + "@example.com";
            signup(email, "distinct_" + index);
            distinctTokens.add(login(email));
        }

        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 6; index++) {
                futures.add(executor.submit(() -> readStatusAfter(start, ideaId, duplicateToken)));
            }
            for (String token : distinctTokens) {
                futures.add(executor.submit(() -> readStatusAfter(start, ideaId, token)));
            }
            start.countDown();
            for (var future : futures) assertThat(future.get()).isEqualTo(200);
        }

        assertThat(currentCount(ideaId)).isEqualTo(5L);
        assertThat(hourlyDeltas(ideaId)).containsExactly(5);
        assertThat(eventCount(ideaId)).isEqualTo(5L);
    }

    @Test
    void publishesTheViewCountOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ideas/{ideaId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.components.schemas.IdeaDetailResponse.properties.viewCount").exists());
    }

    private org.springframework.test.web.servlet.ResultActions read(
            UUID ideaId, String token, MockHttpSession session) throws Exception {
        var request = get("/api/v1/ideas/{ideaId}", ideaId);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (session != null) request.session(session);
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private int readStatusAfter(CountDownLatch start, UUID ideaId, String token) throws Exception {
        start.await();
        return mockMvc.perform(get("/api/v1/ideas/{ideaId}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private long currentCount(UUID ideaId) {
        return jdbc.queryForObject("SELECT view_count FROM idea_metric_current WHERE idea_id=?", Long.class, ideaId);
    }

    private List<Integer> hourlyDeltas(UUID ideaId) {
        return jdbc.queryForList(
                "SELECT view_delta FROM idea_metric_hourly WHERE idea_id=? ORDER BY bucket_hour", Integer.class, ideaId);
    }

    private long eventCount(UUID ideaId) {
        return jdbc.queryForObject("SELECT count(*) FROM idea_view_events WHERE idea_id=?", Long.class, ideaId);
    }

    private UUID publishedIdea(String email, String profileId, String visibility) throws Exception {
        String author = signupAndLogin(email, profileId);
        UUID ideaId = draftIdea(author);
        jdbc.update("""
                UPDATE ideas SET status='PUBLISHED', visibility=?, current_unit_price=10,
                                 published_at=now(), updated_at=now() WHERE id=?
                """, visibility, ideaId);
        return ideaId;
    }

    private UUID draftIdea(String authorToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"조회 지표 아이디어","category":"SERVICE","summary":"조회 지표 요약",
                                 "problem":"조회 지표 문제","targetCustomer":"대상 고객",
                                 "solution":"조회 지표 해결책","businessModel":"조회 지표 수익 모델"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private String signupAndLogin(String email, String profileId) throws Exception {
        signup(email, profileId);
        return login(email);
    }

    private void signup(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockTestConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant = Instant.EPOCH;

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
