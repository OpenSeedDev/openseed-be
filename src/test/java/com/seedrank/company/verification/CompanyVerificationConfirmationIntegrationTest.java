package com.seedrank.company.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

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
class CompanyVerificationConfirmationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM company_verifications");
        jdbc.update("DELETE FROM company_profiles");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void confirmsOnceAndMakesTheLatestCompanyRoleVisibleToExistingAndNewSessions() throws Exception {
        String accessToken = registerCompanyProfile();
        String rawToken = issueVerification(Instant.now().plusSeconds(1800), null, null);

        mockMvc.perform(confirm(rawToken))
                .andExpect(status().isNoContent());

        var state = jdbc.queryForMap("""
                SELECT v.used_at, p.verified_at, u.role
                FROM company_verifications v
                JOIN company_profiles p ON p.id=v.company_profile_id
                JOIN users u ON u.id=p.user_id
                """);
        assertThat(state.get("used_at")).isNotNull().isEqualTo(state.get("verified_at"));
        assertThat(state.get("role")).isEqualTo("COMPANY");

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMPANY"))
                .andExpect(jsonPath("$.companyVerificationStatus").value("VERIFIED"));

        var loggedIn = login("member@example.com");
        assertThat(objectMapper.readTree(loggedIn.body()).get("role").asText()).isEqualTo("COMPANY");
        var refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie("refresh_token", loggedIn.refreshToken())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jwtRole(objectMapper.readTree(refreshed).get("accessToken").asText())).isEqualTo("COMPANY");
    }

    @Test
    void rejectsMissingOrBlankTokensAsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/companies/verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(confirm("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void hidesWhetherATokenIsForgedExpiredUsedOrInvalidated() throws Exception {
        registerCompanyProfile();
        Instant now = Instant.now();
        String expired = issueVerification(now.minusSeconds(1), null, null);
        String used = issueVerification(now.plusSeconds(1800), now.minusSeconds(10), null);
        String invalidated = issueVerification(now.plusSeconds(1800), null, now.minusSeconds(10));

        for (String token : new String[] {"forged-token", expired, used, invalidated}) {
            String response = mockMvc.perform(confirm(token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_COMPANY_VERIFICATION_TOKEN"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain(token, "member@corp.example");
        }
        assertThat(jdbc.queryForObject("SELECT role FROM users", String.class)).isEqualTo("USER");
        assertThat(jdbc.queryForObject("SELECT verified_at IS NULL FROM company_profiles", Boolean.class)).isTrue();
    }

    @Test
    void consumesAValidTokenOnlyOnce() throws Exception {
        registerCompanyProfile();
        String rawToken = issueVerification(Instant.now().plusSeconds(1800), null, null);

        mockMvc.perform(confirm(rawToken)).andExpect(status().isNoContent());
        mockMvc.perform(confirm(rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_COMPANY_VERIFICATION_TOKEN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_verifications WHERE used_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentConfirmationAllowsExactlyOneSuccess() throws Exception {
        registerCompanyProfile();
        String rawToken = issueVerification(Instant.now().plusSeconds(1800), null, null);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> performAfter(start, rawToken));
            var second = executor.submit(() -> performAfter(start, rawToken));
            start.countDown();

            assertThat(new int[] {first.get(), second.get()})
                    .containsExactlyInAnyOrder(204, 401);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_verifications WHERE used_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void publishesTheConfirmationOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/companies/verifications/confirm'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/verifications/confirm'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/verifications/confirm'].post.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyVerificationConfirmationRequest.properties.token").exists());
    }

    private int performAfter(CountDownLatch start, String token) throws Exception {
        start.await();
        return mockMvc.perform(confirm(token)).andReturn().getResponse().getStatus();
    }

    private String registerCompanyProfile() throws Exception {
        signup();
        var login = login("member@example.com");
        mockMvc.perform(post("/api/v1/companies/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"OpenSeed","companyEmail":"member@corp.example"}
                                """))
                .andExpect(status().isCreated());
        return login.accessToken();
    }

    private void signup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member@example.com","password":"password123","profileId":"seed_member"}
                                """))
                .andExpect(status().isCreated());
    }

    private Login login(String email) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String body = response.getContentAsString();
        return new Login(
                objectMapper.readTree(body).get("accessToken").asText(),
                response.getCookie("refresh_token").getValue(),
                body);
    }

    private String issueVerification(Instant expiresAt, Instant usedAt, Instant invalidatedAt) throws Exception {
        String rawToken = UUID.randomUUID() + "-raw";
        UUID profileId = jdbc.queryForObject("SELECT id FROM company_profiles", UUID.class);
        jdbc.update("""
                INSERT INTO company_verifications
                    (id, company_profile_id, token_hash, expires_at, used_at, invalidated_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), profileId, sha256(rawToken), Timestamp.from(expiresAt),
                usedAt == null ? null : Timestamp.from(usedAt),
                invalidatedAt == null ? null : Timestamp.from(invalidatedAt),
                Timestamp.from(expiresAt.minusSeconds(1800)));
        return rawToken;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder confirm(String rawToken) {
        return post("/api/v1/companies/verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(rawToken));
    }

    private String sha256(String rawToken) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private String jwtRole(String accessToken) throws Exception {
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        return objectMapper.readTree(payload).get("role").asText();
    }

    private record Login(String accessToken, String refreshToken, String body) {
    }
}
