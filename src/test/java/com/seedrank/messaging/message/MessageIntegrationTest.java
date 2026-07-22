package com.seedrank.messaging.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
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
class MessageIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clean() {
        if (jdbc.queryForObject("SELECT to_regclass('message_thread_messages') IS NOT NULL", Boolean.class)) {
            jdbc.update("DELETE FROM message_thread_messages");
        }
        jdbc.update("DELETE FROM message_threads");
        jdbc.update("DELETE FROM company_interests");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
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
    void companyParticipantSendsTextForEveryPublishedVisibilityWithoutReadState() throws Exception {
        UserSession company = verifiedCompany("company@acme.test", "acme_company", "Acme");

        for (String visibility : new String[] {"PUBLIC", "SEMI_PUBLIC", "MATCHING"}) {
            PublishedIdea idea = publishedIdea("author-" + visibility + "@example.com", visibility);
            UUID threadId = createThread(company.token(), idea.id());

            send(company.token(), threadId, "  협업을 제안합니다.  ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isString())
                    .andExpect(jsonPath("$.content").value("협업을 제안합니다."))
                    .andExpect(jsonPath("$.senderId").value(company.userId().toString()))
                    .andExpect(jsonPath("$.sentAt").isString())
                    .andExpect(jsonPath("$.read").doesNotExist())
                    .andExpect(jsonPath("$.readAt").doesNotExist())
                    .andExpect(jsonPath("$.unreadCount").doesNotExist());
        }
    }

    @Test
    void ideaAuthorRepliesAndBothParticipantsReadTheSameMessages() throws Exception {
        UserSession company = verifiedCompany("reply@acme.test", "reply_company", "Acme");
        PublishedIdea idea = publishedIdea("reply-author@example.com", "MATCHING");
        UUID threadId = createThread(company.token(), idea.id());

        send(company.token(), threadId, "안녕하세요").andExpect(status().isCreated());
        send(idea.authorToken(), threadId, "문의 감사합니다").andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(idea.authorId().toString()));

        for (String token : new String[] {company.token(), idea.authorToken()}) {
            list(token, threadId, null, 20)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].content").value("안녕하세요"))
                    .andExpect(jsonPath("$.items[1].content").value("문의 감사합니다"))
                    .andExpect(jsonPath("$.items[0].read").doesNotExist())
                    .andExpect(jsonPath("$.nextCursor").doesNotExist())
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    @Test
    void nonParticipantAndMissingThreadUseTheSameNotFoundContract() throws Exception {
        UserSession company = verifiedCompany("private@acme.test", "private_company", "Acme");
        PublishedIdea idea = publishedIdea("private-author@example.com", "PUBLIC");
        UUID threadId = createThread(company.token(), idea.id());
        UserSession outsider = signupAndLogin("outsider@example.com", "outsider");

        send(outsider.token(), threadId, "볼 수 없어야 함")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_THREAD_NOT_FOUND"));
        list(outsider.token(), threadId, null, 20)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_THREAD_NOT_FOUND"));
        send(company.token(), UUID.randomUUID(), "없는 스레드")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_THREAD_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_thread_messages", Integer.class)).isZero();
    }

    @Test
    void requiresAuthenticationForSendAndList() throws Exception {
        UUID threadId = UUID.randomUUID();

        send(null, threadId, "인증 없음").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        list("not-a-token", threadId, null, 20).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void rejectsBlankAndOverlongTextWithoutSaving() throws Exception {
        UserSession company = verifiedCompany("validation@acme.test", "validation_company", "Acme");
        PublishedIdea idea = publishedIdea("validation-author@example.com", "SEMI_PUBLIC");
        UUID threadId = createThread(company.token(), idea.id());

        send(company.token(), threadId, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(company.token(), threadId, "가".repeat(2001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_thread_messages", Integer.class)).isZero();
    }

    @Test
    void rejectsMalformedCursorAndOutOfRangePageSize() throws Exception {
        UserSession company = verifiedCompany("page@acme.test", "page_company", "Acme");
        PublishedIdea idea = publishedIdea("page-author@example.com", "PUBLIC");
        UUID threadId = createThread(company.token(), idea.id());

        list(company.token(), threadId, "not-a-cursor", 20)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        list(company.token(), threadId, null, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        list(company.token(), threadId, null, 101)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void cursorHasNoDuplicatesOrGapsWhenSentTimesAreEqual() throws Exception {
        UserSession company = verifiedCompany("cursor@acme.test", "cursor_company", "Acme");
        PublishedIdea idea = publishedIdea("cursor-author@example.com", "PUBLIC");
        UUID threadId = createThread(company.token(), idea.id());
        for (int index = 1; index <= 5; index++) {
            send(company.token(), threadId, "메시지 " + index).andExpect(status().isCreated());
        }
        Instant sameTime = Instant.parse("2026-07-22T07:30:00Z");
        jdbc.update("UPDATE message_thread_messages SET sent_at=? WHERE thread_id=?",
                Timestamp.from(sameTime), threadId);

        JsonNode first = json(list(company.token(), threadId, null, 2));
        JsonNode second = json(list(company.token(), threadId, first.get("nextCursor").asText(), 2));
        JsonNode third = json(list(company.token(), threadId, second.get("nextCursor").asText(), 2));

        var ids = new HashSet<String>();
        first.get("items").forEach(item -> ids.add(item.get("id").asText()));
        second.get("items").forEach(item -> ids.add(item.get("id").asText()));
        third.get("items").forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).hasSize(5);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(second.get("hasNext").asBoolean()).isTrue();
        assertThat(third.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void sendingUpdatesThreadTimestampAndPublishesOpenApiWithoutReadFields() throws Exception {
        UserSession company = verifiedCompany("openapi@acme.test", "openapi_company", "Acme");
        PublishedIdea idea = publishedIdea("openapi-author@example.com", "PUBLIC");
        UUID threadId = createThread(company.token(), idea.id());
        Instant before = jdbc.queryForObject(
                "SELECT updated_at FROM message_threads WHERE id=?", Timestamp.class, threadId).toInstant();

        send(company.token(), threadId, "갱신 시각 확인").andExpect(status().isCreated());
        Instant after = jdbc.queryForObject(
                "SELECT updated_at FROM message_threads WHERE id=?", Timestamp.class, threadId).toInstant();
        assertThat(after).isAfterOrEqualTo(before);

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/message-threads/{threadId}/messages'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/message-threads/{threadId}/messages'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.schemas.MessageResponse.properties.read").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MessageResponse.properties.readAt").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MessagePageResponse.properties.unreadCount").doesNotExist());
    }

    private ResultActions send(String token, UUID threadId, String content) throws Exception {
        var request = post("/api/v1/message-threads/{threadId}/messages", threadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("content", content)));
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(request);
    }

    private ResultActions list(String token, UUID threadId, String cursor, int size) throws Exception {
        var request = get("/api/v1/message-threads/{threadId}/messages", threadId).param("size", String.valueOf(size));
        if (cursor != null) request.param("cursor", cursor);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private UUID createThread(String token, UUID ideaId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/ideas/{ideaId}/message-thread", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UserSession verifiedCompany(String email, String profileId, String companyName) throws Exception {
        UserSession session = signupAndLogin(email, profileId);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO company_profiles
                    (id, user_id, company_name, company_email, company_domain, verified_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), session.userId(), companyName, email, "acme.test",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("UPDATE users SET role='COMPANY' WHERE id=?", session.userId());
        return session;
    }

    private PublishedIdea publishedIdea(String authorEmail, String visibility) throws Exception {
        UserSession author = signupAndLogin(authorEmail, "author_" + UUID.randomUUID().toString().substring(0, 8));
        String body = mockMvc.perform(post("/api/v1/ideas/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + author.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"문의 대상\",\"category\":\"SERVICE\",\"problem\":\"문제\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID ideaId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE ideas SET status='PUBLISHED', visibility=?, current_unit_price=10,
                    published_at=?, updated_at=? WHERE id=?
                """, visibility, Timestamp.from(now), Timestamp.from(now), ideaId);
        return new PublishedIdea(ideaId, author.userId(), author.token());
    }

    private UserSession signupAndLogin(String email, String profileId) throws Exception {
        String signupBody = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"%s\"}"
                                .formatted(email, profileId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(signupBody).get("userId").asText());
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new UserSession(userId, objectMapper.readTree(body).get("accessToken").asText());
    }

    private record UserSession(UUID userId, String token) {}
    private record PublishedIdea(UUID id, UUID authorId, String authorToken) {}
}
