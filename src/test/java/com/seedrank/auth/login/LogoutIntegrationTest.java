package com.seedrank.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import org.springframework.mock.web.MockCookie;
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
class LogoutIntegrationTest {
    private static final String JWT_SECRET = "test-signing-key-with-at-least-32-bytes";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @AfterEach
    void dropFailureTriggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_logout_update ON auth_sessions");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_logout_update()");
        jdbc.execute("DROP SEQUENCE IF EXISTS logout_attempt_seq");
        jdbc.execute("DROP TRIGGER IF EXISTS slow_session_rotation ON auth_sessions");
        jdbc.execute("DROP FUNCTION IF EXISTS slow_session_rotation()");
        jdbc.execute("DROP SEQUENCE IF EXISTS session_rotation_started_seq");
        jdbc.execute("DROP TRIGGER IF EXISTS slow_session_insert ON auth_sessions");
        jdbc.execute("DROP FUNCTION IF EXISTS slow_session_insert()");
        jdbc.execute("DROP SEQUENCE IF EXISTS session_insert_started_seq");
    }

    @Test
    void logoutRevokesOnlyTheCurrentFamilyAndIsIdempotent() throws Exception {
        signup("member@example.com");
        Tokens first = login("member@example.com");
        Tokens second = login("member@example.com");

        mockMvc.perform(logout(first.refreshToken()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        assertThat(reasonFor(first.refreshToken())).isEqualTo("LOGOUT");
        assertThat(reasonFor(second.refreshToken())).isNull();
        mockMvc.perform(refresh(first.refreshToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(second.refreshToken())).andExpect(status().isOk());
        mockMvc.perform(logout(first.refreshToken())).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
        mockMvc.perform(logout("unknown-refresh-token")).andExpect(status().isNoContent());

        mockMvc.perform(logoutAll(first.accessToken(), null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void logoutAllRevokesOnlyTheAuthenticatedUsersSessions() throws Exception {
        signup("member@example.com");
        Tokens first = login("member@example.com");
        Tokens second = login("member@example.com");
        signup("other@example.com");
        Tokens other = login("other@example.com");

        mockMvc.perform(logoutAll(second.accessToken(), second.refreshToken()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        UUID memberId = userId("member@example.com");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=? AND revocation_reason='LOGOUT_ALL'", Integer.class, memberId))
                .isEqualTo(2);
        assertThat(reasonFor(other.refreshToken())).isNull();
        mockMvc.perform(refresh(first.refreshToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(second.refreshToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(other.refreshToken())).andExpect(status().isOk());
    }

    @Test
    void logoutAllRejectsMissingForgedExpiredAndSidlessAccessTokens() throws Exception {
        signup("member@example.com");
        Tokens tokens = login("member@example.com");
        UUID userId = userId("member@example.com");

        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(logoutAll(tokens.accessToken() + "forged", null))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(logoutAll(signedAccess(userId, UUID.randomUUID(), Instant.now().minusSeconds(1), true), null))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(logoutAll(signedAccess(userId, null, Instant.now().plusSeconds(900), false), null))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void loginAndRefreshAccessTokensAreBoundToTheirSessions() throws Exception {
        signup("member@example.com");
        Tokens login = login("member@example.com");
        UUID loginSessionId = sessionId(login.refreshToken());
        assertAccessSession(login.accessToken(), loginSessionId);

        var refreshResult = mockMvc.perform(refresh(login.refreshToken()))
                .andExpect(status().isOk()).andReturn().getResponse();
        String nextRefresh = refreshResult.getCookie("refresh_token").getValue();
        String nextAccess = objectMapper.readTree(refreshResult.getContentAsString()).get("accessToken").asText();
        assertAccessSession(nextAccess, sessionId(nextRefresh));

        mockMvc.perform(logoutAll(login.accessToken(), null)).andExpect(status().isUnauthorized());
        mockMvc.perform(logoutAll(nextAccess, nextRefresh)).andExpect(status().isNoContent());
        assertThat(decodePayload(nextAccess).toString())
                .doesNotContain("member@example.com", "open_seed", nextRefresh);
    }

    @Test
    void concurrentRefreshAndCurrentLogoutLeaveNoActiveFamilySession() throws Exception {
        signup("member@example.com");
        Tokens tokens = login("member@example.com");
        UUID familyId = jdbc.queryForObject("SELECT family_id FROM auth_sessions WHERE refresh_token_hash=?", UUID.class, sha256(tokens.refreshToken()));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var refreshTask = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown(); start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(refresh(tokens.refreshToken())).andReturn().getResponse().getStatus();
            };
            var logoutTask = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown(); start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(logout(tokens.refreshToken())).andReturn().getResponse().getStatus();
            };
            var refreshStatus = executor.submit(refreshTask);
            var logoutStatus = executor.submit(logoutTask);
            ready.await(5, TimeUnit.SECONDS); start.countDown();
            assertThat(logoutStatus.get()).isEqualTo(204);
            assertThat(List.of(200, 401)).contains(refreshStatus.get());
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE family_id=? AND revoked_at IS NULL", Integer.class, familyId))
                .isZero();
    }

    @Test
    void logoutAllWaitsForAnInFlightRefreshAndRevokesItsNewSession() throws Exception {
        signup("member@example.com");
        Tokens tokens = login("member@example.com");
        installSlowRotationTrigger();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var refreshResult = executor.submit(() -> mockMvc.perform(refresh(tokens.refreshToken()))
                    .andReturn().getResponse().getStatus());
            awaitSequenceCall("session_rotation_started_seq");

            mockMvc.perform(logoutAll(tokens.accessToken(), tokens.refreshToken()))
                    .andExpect(status().isNoContent());
            assertThat(refreshResult.get()).isEqualTo(200);
        }

        assertThat(activeSessionCount(userId("member@example.com"))).isZero();
    }

    @Test
    void logoutAllWaitsForAnInFlightLoginAndRevokesTheCreatedSession() throws Exception {
        signup("member@example.com");
        Tokens current = login("member@example.com");
        installSlowInsertTrigger();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var loginResult = executor.submit(() -> login("member@example.com"));
            awaitSequenceCall("session_insert_started_seq");

            mockMvc.perform(logoutAll(current.accessToken(), current.refreshToken()))
                    .andExpect(status().isNoContent());
            assertThat(loginResult.get().accessToken()).isNotBlank();
        }

        assertThat(activeSessionCount(userId("member@example.com"))).isZero();
    }

    @Test
    void rollsBackCurrentAndAllSessionLogoutWhenRevocationFails() throws Exception {
        signup("member@example.com");
        Tokens first = login("member@example.com");
        Tokens second = login("member@example.com");
        installLogoutFailureTrigger("LOGOUT");
        mockMvc.perform(logout(first.refreshToken())).andExpect(status().isInternalServerError());
        assertThat(logoutTriggerWasCalled()).isTrue();
        assertThat(activeSessionCount(userId("member@example.com"))).isEqualTo(2);

        dropFailureTriggers();
        installLogoutFailureTrigger("LOGOUT_ALL");
        mockMvc.perform(logoutAll(second.accessToken(), second.refreshToken())).andExpect(status().isInternalServerError());
        assertThat(logoutTriggerWasCalled()).isTrue();
        assertThat(activeSessionCount(userId("member@example.com"))).isEqualTo(2);
    }

    @Test
    void publishesLogoutContractsAndBearerSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout-all'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout-all'].post.responses['401']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    private void signup(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\",\"profileId\":\"open_seed\"}".formatted(email)))
                .andExpect(status().isCreated());
    }

    private Tokens login(String email) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Tokens(
                objectMapper.readTree(response.getContentAsString()).get("accessToken").asText(),
                response.getCookie("refresh_token").getValue());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String refreshToken) {
        return post("/api/v1/auth/refresh").cookie(new MockCookie("refresh_token", refreshToken));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder logout(String refreshToken) {
        return post("/api/v1/auth/logout").cookie(new MockCookie("refresh_token", refreshToken));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder logoutAll(String accessToken, String refreshToken) {
        var request = post("/api/v1/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return refreshToken == null ? request : request.cookie(new MockCookie("refresh_token", refreshToken));
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
    }

    private UUID sessionId(String refreshToken) throws Exception {
        return jdbc.queryForObject("SELECT id FROM auth_sessions WHERE refresh_token_hash=?", UUID.class, sha256(refreshToken));
    }

    private String reasonFor(String refreshToken) throws Exception {
        return jdbc.queryForObject("SELECT revocation_reason FROM auth_sessions WHERE refresh_token_hash=?", String.class, sha256(refreshToken));
    }

    private int activeSessionCount(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NULL", Integer.class, userId);
    }

    private void assertAccessSession(String accessToken, UUID sessionId) throws Exception {
        JsonNode payload = decodePayload(accessToken);
        assertThat(payload.get("sid").asText()).isEqualTo(sessionId.toString());
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(900);
    }

    private JsonNode decodePayload(String token) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }

    private String signedAccess(UUID userId, UUID sessionId, Instant expiresAt, boolean includeSid) throws Exception {
        String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String sid = includeSid ? ",\"sid\":\"" + sessionId + "\"" : "";
        String payload = base64("{\"sub\":\"%s\",\"role\":\"USER\",\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"%s}"
                .formatted(userId, expiresAt.minusSeconds(900).getEpochSecond(), expiresAt.getEpochSecond(), UUID.randomUUID(), sid));
        String content = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return content + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void installLogoutFailureTrigger(String reason) {
        jdbc.execute("CREATE SEQUENCE logout_attempt_seq");
        jdbc.execute("CREATE FUNCTION reject_logout_update() RETURNS trigger AS $$ BEGIN IF NEW.revocation_reason='" + reason
                + "' THEN PERFORM nextval('logout_attempt_seq'); RAISE EXCEPTION 'forced'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER reject_logout_update BEFORE UPDATE ON auth_sessions FOR EACH ROW EXECUTE FUNCTION reject_logout_update()");
    }

    private boolean logoutTriggerWasCalled() {
        return jdbc.queryForObject("SELECT is_called FROM logout_attempt_seq", Boolean.class);
    }

    private void installSlowRotationTrigger() {
        jdbc.execute("CREATE SEQUENCE session_rotation_started_seq");
        jdbc.execute("CREATE FUNCTION slow_session_rotation() RETURNS trigger AS $$ BEGIN "
                + "IF NEW.revocation_reason='ROTATED' THEN PERFORM nextval('session_rotation_started_seq'); PERFORM pg_sleep(0.5); END IF; "
                + "RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER slow_session_rotation BEFORE UPDATE ON auth_sessions FOR EACH ROW EXECUTE FUNCTION slow_session_rotation()");
    }

    private void installSlowInsertTrigger() {
        jdbc.execute("CREATE SEQUENCE session_insert_started_seq");
        jdbc.execute("CREATE FUNCTION slow_session_insert() RETURNS trigger AS $$ BEGIN "
                + "PERFORM nextval('session_insert_started_seq'); PERFORM pg_sleep(0.5); RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER slow_session_insert BEFORE INSERT ON auth_sessions FOR EACH ROW EXECUTE FUNCTION slow_session_insert()");
    }

    private void awaitSequenceCall(String sequence) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (jdbc.queryForObject("SELECT is_called FROM " + sequence, Boolean.class)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for " + sequence);
    }

    private record Tokens(String accessToken, String refreshToken) {}
}
