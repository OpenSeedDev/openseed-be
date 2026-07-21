package com.seedrank.company.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
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
@Import({TestcontainersConfiguration.class, CompanyVerificationIntegrationTest.MailTestConfiguration.class})
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.company-verification.expires-in=PT30M",
        "app.company-verification.confirm-url=https://frontend.example/companies/verification/confirm",
        "app.company-verification.mail-from=no-reply@seedrank.example",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class CompanyVerificationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        mailSender.reset();
        jdbc.update("DELETE FROM company_verifications");
        jdbc.update("DELETE FROM company_profiles");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void createsAHashedExpiringTokenAndSendsTheRawTokenAfterCommitOnAnAsyncThread() throws Exception {
        String accessToken = registerCompanyProfile();

        String response = mockMvc.perform(sendVerification(accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Delivery delivery = mailSender.awaitDelivery();
        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT token_hash, expires_at, used_at, invalidated_at
                FROM company_verifications
                """);
        Instant expiresAt = Instant.parse(objectMapper.readTree(response).get("expiresAt").asText());

        assertThat(delivery.email()).isEqualTo("member@corp.example");
        assertThat(delivery.rawToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(delivery.expiresAt()).isEqualTo(expiresAt);
        assertThat(delivery.threadName()).startsWith("company-verification-");
        assertThat(delivery.committedRowObserved()).isTrue();
        assertThat(stored.get("token_hash")).isEqualTo(sha256(delivery.rawToken()));
        assertThat(stored.get("used_at")).isNull();
        assertThat(stored.get("invalidated_at")).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT expires_at - created_at = interval '30 minutes'
                FROM company_verifications
                """, Boolean.class)).isTrue();
        assertThat(response).doesNotContain(delivery.rawToken(), delivery.email(), "companyEmail", "token");
    }

    @Test
    void resendingInvalidatesThePreviousTokenAndLeavesOnlyOneActiveVerification() throws Exception {
        String accessToken = registerCompanyProfile();

        mockMvc.perform(sendVerification(accessToken)).andExpect(status().isAccepted());
        Delivery first = mailSender.awaitDelivery();
        mockMvc.perform(sendVerification(accessToken)).andExpect(status().isAccepted());
        Delivery second = mailSender.awaitDelivery();

        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_verifications", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM company_verifications
                WHERE used_at IS NULL AND invalidated_at IS NULL
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM company_verifications
                WHERE token_hash = ? AND invalidated_at IS NOT NULL
                """, Integer.class, sha256(first.rawToken()))).isEqualTo(1);
    }

    @Test
    void requiresAnActiveSessionAndACompanyProfileThatIsNotYetVerified() throws Exception {
        signup("member@example.com", "seed_member");
        String accessToken = login("member@example.com");

        mockMvc.perform(post("/api/v1/companies/verifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(sendVerification(accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_PROFILE_REQUIRED"));

        createCompanyProfile(accessToken);
        jdbc.update("UPDATE auth_sessions SET revoked_at=now(), revocation_reason='LOGOUT'");
        mockMvc.perform(sendVerification(accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        String renewedAccessToken = login("member@example.com");
        jdbc.update("UPDATE company_profiles SET verified_at=now()");
        mockMvc.perform(sendVerification(renewedAccessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_VERIFIED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_verifications", Integer.class)).isZero();
    }

    @Test
    void acceptsTheRequestEvenWhenTheAsyncMailProviderFails() throws Exception {
        String accessToken = registerCompanyProfile();
        mailSender.failNext();

        mockMvc.perform(sendVerification(accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        assertThat(mailSender.awaitAttempt()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_verifications", Integer.class)).isEqualTo(1);
    }

    @Test
    void publishesTheVerificationMailOpenApiContractWithoutSensitiveResponseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/companies/verifications'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/verifications'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/verifications'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/verifications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyVerificationResponse.properties.expiresAt").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyVerificationResponse.properties.token").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CompanyVerificationResponse.properties.companyEmail").doesNotExist());
    }

    private String registerCompanyProfile() throws Exception {
        signup("member@example.com", "seed_member");
        String accessToken = login("member@example.com");
        createCompanyProfile(accessToken);
        return accessToken;
    }

    private void signup(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","profileId":"%s"}
                                """.formatted(email, profileId)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private void createCompanyProfile(String accessToken) throws Exception {
        mockMvc.perform(post("/api/v1/companies/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"OpenSeed","companyEmail":"member@corp.example"}
                                """))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sendVerification(
            String accessToken) {
        return post("/api/v1/companies/verifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private String sha256(String rawToken) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration
    static class MailTestConfiguration {
        @Bean
        @Primary
        RecordingMailSender recordingMailSender(JdbcTemplate jdbc) {
            return new RecordingMailSender(jdbc);
        }
    }

    static class RecordingMailSender implements CompanyVerificationMailSender {
        private final JdbcTemplate jdbc;
        private final BlockingQueue<Delivery> deliveries = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> attempts = new LinkedBlockingQueue<>();
        private volatile boolean failNext;

        RecordingMailSender(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void send(String companyEmail, String rawToken, Instant expiresAt) {
            boolean committed = jdbc.queryForObject("SELECT count(*) FROM company_verifications", Integer.class) > 0;
            attempts.add(true);
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("provider unavailable");
            }
            deliveries.add(new Delivery(companyEmail, rawToken, expiresAt, Thread.currentThread().getName(), committed));
        }

        Delivery awaitDelivery() throws InterruptedException {
            Delivery delivery = deliveries.poll(5, TimeUnit.SECONDS);
            assertThat(delivery).as("비동기 인증 메일").isNotNull();
            return delivery;
        }

        boolean awaitAttempt() throws InterruptedException {
            return Boolean.TRUE.equals(attempts.poll(5, TimeUnit.SECONDS));
        }

        void failNext() {
            failNext = true;
        }

        void reset() {
            deliveries.clear();
            attempts.clear();
            failNext = false;
        }
    }

    private record Delivery(
            String email,
            String rawToken,
            Instant expiresAt,
            String threadName,
            boolean committedRowObserved) {
    }
}
