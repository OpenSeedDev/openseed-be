package com.seedrank.member.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
class MyAccountIntegrationTest {
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

    @Test
    void returnsTheAuthenticatedUsersCurrentAccountWithoutSensitiveFields() throws Exception {
        signup("member@example.com", "seed_member");
        Tokens tokens = login("member@example.com");
        UUID userId = userId("member@example.com");

        var response = mockMvc.perform(me(tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.profileId").value("seed_member"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.companyVerificationStatus").value("NOT_STARTED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response)
                .doesNotContain("member@example.com", "password", "accessToken", "refreshToken", "sessionId");
        assertThat(objectMapper.readTree(response).size()).isEqualTo(4);
    }

    @Test
    void identifiesUsersByInternalIdWhenProfileIdsAreDuplicated() throws Exception {
        signup("first@example.com", "same_profile");
        signup("second@example.com", "same_profile");
        Tokens first = login("first@example.com");
        Tokens second = login("second@example.com");

        mockMvc.perform(me(first.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId("first@example.com").toString()))
                .andExpect(jsonPath("$.profileId").value("same_profile"));
        mockMvc.perform(me(second.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId("second@example.com").toString()))
                .andExpect(jsonPath("$.profileId").value("same_profile"));
    }

    @Test
    void doesNotChangeTheUserOrAuthenticationSession() throws Exception {
        signup("member@example.com", "seed_member");
        Tokens tokens = login("member@example.com");
        String userUpdatedAt = jdbc.queryForObject(
                "SELECT updated_at::text FROM users WHERE email='member@example.com'", String.class);
        var sessionBefore = jdbc.queryForMap("""
                SELECT expires_at::text, revoked_at::text, revocation_reason
                FROM auth_sessions
                """);

        mockMvc.perform(me(tokens.accessToken())).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT updated_at::text FROM users WHERE email='member@example.com'", String.class))
                .isEqualTo(userUpdatedAt);
        assertThat(jdbc.queryForMap("""
                SELECT expires_at::text, revoked_at::text, revocation_reason
                FROM auth_sessions
                """)).isEqualTo(sessionBefore);
    }

    @Test
    void rejectsMissingMalformedForgedExpiredAndSidlessAccessTokens() throws Exception {
        signup("member@example.com", "seed_member");
        Tokens tokens = login("member@example.com");
        UUID userId = userId("member@example.com");

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Token " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(me(tokens.accessToken() + "forged"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(me(signedAccess(userId, sessionId(), Instant.now().minusSeconds(1), true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(me(signedAccess(userId, null, Instant.now().plusSeconds(900), false)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRevokedAndRotatedSessionsButAcceptsTheNewAccessToken() throws Exception {
        signup("member@example.com", "seed_member");
        Tokens loggedIn = login("member@example.com");

        var refreshedResponse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie("refresh_token", loggedIn.refreshToken())))
                .andExpect(status().isOk()).andReturn().getResponse();
        String refreshedAccess = objectMapper.readTree(refreshedResponse.getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(me(loggedIn.accessToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(me(refreshedAccess)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new MockCookie("refresh_token", refreshedResponse.getCookie("refresh_token").getValue())))
                .andExpect(status().isNoContent());
        mockMvc.perform(me(refreshedAccess)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSuspendedAndMismatchedUsersWithoutLeakingIdentity() throws Exception {
        signup("member@example.com", "seed_member");
        Tokens tokens = login("member@example.com");
        UUID sessionId = sessionId();

        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE email='member@example.com'");
        var suspended = mockMvc.perform(me(tokens.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(suspended).doesNotContain("member@example.com", "seed_member");

        jdbc.update("UPDATE users SET status='ACTIVE' WHERE email='member@example.com'");
        mockMvc.perform(me(signedAccess(UUID.randomUUID(), sessionId, Instant.now().plusSeconds(900), true)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void publishesTheMyAccountOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.MyAccountResponse.properties.userId").exists())
                .andExpect(jsonPath("$.components.schemas.MyAccountResponse.properties.profileId").exists())
                .andExpect(jsonPath("$.components.schemas.MyAccountResponse.properties.role").exists())
                .andExpect(jsonPath("$.components.schemas.MyAccountResponse.properties.companyVerificationStatus").exists());
    }

    private void signup(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123","profileId":"%s"}
                        """.formatted(email, profileId)))
                .andExpect(status().isCreated());
    }

    private Tokens login(String email) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Tokens(
                objectMapper.readTree(response.getContentAsString()).get("accessToken").asText(),
                response.getCookie("refresh_token").getValue());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder me(String accessToken) {
        return get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
    }

    private UUID sessionId() {
        return jdbc.queryForObject("SELECT id FROM auth_sessions WHERE revoked_at IS NULL", UUID.class);
    }

    private String signedAccess(UUID userId, UUID sessionId, Instant expiresAt, boolean includeSid) throws Exception {
        String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String sid = includeSid ? ",\"sid\":\"" + sessionId + "\"" : "";
        String payload = base64("{\"sub\":\"%s\",\"role\":\"USER\",\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"%s}"
                .formatted(userId, expiresAt.minusSeconds(900).getEpochSecond(), expiresAt.getEpochSecond(),
                        UUID.randomUUID(), sid));
        String content = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return content + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
