package com.seedrank.company.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
class CompanyProfileIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM company_profiles");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void registersANormalizedCompanyProfileWithoutExposingTheEmail() throws Exception {
        signup("member@example.com", "seed_member");
        String accessToken = login("member@example.com");

        String response = mockMvc.perform(create(accessToken, "  Open Seed  ", "  Mina@Example.COM  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyProfileId").isNotEmpty())
                .andExpect(jsonPath("$.companyName").value("Open Seed"))
                .andExpect(jsonPath("$.companyDomain").value("example.com"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("Mina@", "mina@example.com", "companyEmail");
        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT company_name, company_email, company_domain, verified_at
                FROM company_profiles
                """);
        assertThat(stored.get("company_name")).isEqualTo("Open Seed");
        assertThat(stored.get("company_email")).isEqualTo("mina@example.com");
        assertThat(stored.get("company_domain")).isEqualTo("example.com");
        assertThat(stored.get("verified_at")).isNull();
    }

    @Test
    void changesMyAccountStatusFromNotStartedToPending() throws Exception {
        signup("member@example.com", "seed_member");
        String accessToken = login("member@example.com");

        mockMvc.perform(me(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyVerificationStatus").value("NOT_STARTED"));
        mockMvc.perform(create(accessToken, "OpenSeed", "member@corp.example"))
                .andExpect(status().isCreated());
        mockMvc.perform(me(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyVerificationStatus").value("PENDING"));
    }

    @Test
    void rejectsFreePersonalEmailDomainsAndTheirSubdomains() throws Exception {
        signup("member@example.com", "seed_member");
        String accessToken = login("member@example.com");

        for (String email : new String[] {
                "member@gmail.com", "member@mail.NAVER.com", "member@daum.net", "member@kakao.com",
                "member@outlook.com", "member@hotmail.com", "member@yahoo.com", "member@icloud.com",
                "member@proton.me"
        }) {
            String body = mockMvc.perform(create(accessToken, "OpenSeed", email))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMPANY_EMAIL_DOMAIN_NOT_ALLOWED"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(email);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_profiles", Integer.class)).isZero();
    }

    @Test
    void rejectsInvalidCompanyNamesAndEmails() throws Exception {
        signup("member@example.com", "seed_member");
        String accessToken = login("member@example.com");

        mockMvc.perform(create(accessToken, "   ", "member@corp.example"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(create(accessToken, "OpenSeed", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(create(accessToken, "x".repeat(101), "member@corp.example"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAValidActiveAccessSession() throws Exception {
        signup("member@example.com", "seed_member");
        String accessToken = login("member@example.com");

        mockMvc.perform(post("/api/v1/companies/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"OpenSeed\",\"companyEmail\":\"member@corp.example\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        jdbc.update("UPDATE auth_sessions SET revoked_at=now(), revocation_reason='LOGOUT'");
        mockMvc.perform(create(accessToken, "OpenSeed", "member@corp.example"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsASecondProfileOrACompanyEmailAlreadyRegisteredByAnotherUser() throws Exception {
        signup("first@example.com", "first_user");
        signup("second@example.com", "second_user");
        String first = login("first@example.com");
        String second = login("second@example.com");

        mockMvc.perform(create(first, "OpenSeed", "member@corp.example"))
                .andExpect(status().isCreated());
        mockMvc.perform(create(first, "Another", "first@another.example"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_PROFILE_ALREADY_EXISTS"));
        mockMvc.perform(create(second, "OpenSeed", "MEMBER@CORP.EXAMPLE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_PROFILE_ALREADY_EXISTS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_profiles", Integer.class)).isEqualTo(1);
    }

    @Test
    void mapsConcurrentUniqueConstraintConflictsToCompanyProfileAlreadyExists() throws Exception {
        signup("same-user@example.com", "same_user");
        signup("first@example.com", "first_user");
        signup("second@example.com", "second_user");
        String sameUser = login("same-user@example.com");
        String first = login("first@example.com");
        String second = login("second@example.com");

        var duplicateUserResponses = performConcurrently(
                () -> create(sameUser, "OpenSeed", "same-user@first.example"),
                () -> create(sameUser, "OpenSeed", "same-user@second.example"));
        assertOneCreatedAndOneConflict(duplicateUserResponses);

        var duplicateEmailResponses = performConcurrently(
                () -> create(first, "OpenSeed", "shared@corp.example"),
                () -> create(second, "OpenSeed", "SHARED@CORP.EXAMPLE"));
        assertOneCreatedAndOneConflict(duplicateEmailResponses);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_profiles", Integer.class)).isEqualTo(2);
    }

    @Test
    void publishesTheCompanyProfileOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/companies/profile'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/profile'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/profile'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/profile'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/companies/profile'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyProfileRequest.properties.companyName").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyProfileRequest.properties.companyEmail").exists())
                .andExpect(jsonPath("$.components.schemas.CompanyProfileResponse.properties.companyEmail").doesNotExist());
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(
            String accessToken, String companyName, String companyEmail) throws Exception {
        return post("/api/v1/companies/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "companyName", companyName,
                        "companyEmail", companyEmail)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder me(String accessToken) {
        return get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private java.util.List<ConcurrentResponse> performConcurrently(
            RequestFactory first,
            RequestFactory second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResponse = executor.submit(() -> performAfterBarrier(first, ready, start));
            var secondResponse = executor.submit(() -> performAfterBarrier(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.List.of(
                    firstResponse.get(10, TimeUnit.SECONDS),
                    secondResponse.get(10, TimeUnit.SECONDS));
        }
    }

    private ConcurrentResponse performAfterBarrier(
            RequestFactory request,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        var response = mockMvc.perform(request.create()).andReturn().getResponse();
        return new ConcurrentResponse(response.getStatus(), response.getContentAsString());
    }

    private void assertOneCreatedAndOneConflict(java.util.List<ConcurrentResponse> responses) throws Exception {
        assertThat(responses).extracting(ConcurrentResponse::status).containsExactlyInAnyOrder(201, 409);
        String conflictBody = responses.stream()
                .filter(response -> response.status() == 409)
                .findFirst()
                .orElseThrow()
                .body();
        assertThat(objectMapper.readTree(conflictBody).get("code").asText())
                .isEqualTo("COMPANY_PROFILE_ALREADY_EXISTS");
    }

    @FunctionalInterface
    private interface RequestFactory {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create() throws Exception;
    }

    private record ConcurrentResponse(int status, String body) {
    }
}
