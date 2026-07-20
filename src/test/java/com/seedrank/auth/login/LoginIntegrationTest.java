package com.seedrank.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.auth.cookie-secure=false"
})
@AutoConfigureMockMvc
class LoginIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM auth_sessions");
        jdbcTemplate.update("DELETE FROM point_ledgers");
        jdbcTemplate.update("DELETE FROM point_wallets");
        jdbcTemplate.update("DELETE FROM users");
    }

    @AfterEach void dropTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_auth_session_insert ON auth_sessions");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_auth_session_insert()");
    }

    @Test
    void logsInAndCreatesHashedRefreshSession() throws Exception {
        signup("Member@Example.com", "password123");

        var result = mockMvc.perform(login("  MEMBER@EXAMPLE.COM ", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", false))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                .andReturn();

        String refresh = result.getResponse().getCookie("refresh_token").getValue();
        String storedHash = jdbcTemplate.queryForObject("SELECT refresh_token_hash FROM auth_sessions", String.class);
        assertThat(storedHash).isNotEqualTo(refresh).isEqualTo(sha256(refresh));
        Long lifetime = jdbcTemplate.queryForObject("SELECT extract(epoch FROM (expires_at-created_at))::bigint FROM auth_sessions", Long.class);
        assertThat(lifetime).isEqualTo(14 * 24 * 60 * 60L);
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");

        String access = result.getResponse().getContentAsString();
        assertThat(access).doesNotContain("member@example.com").doesNotContain("open_seed");
    }

    @Test
    void consecutiveLoginsCreateDifferentSessions() throws Exception {
        signup("member@example.com", "password123");
        String first = mockMvc.perform(login("member@example.com", "password123")).andReturn()
                .getResponse().getCookie("refresh_token").getValue();
        String second = mockMvc.perform(login("member@example.com", "password123")).andReturn()
                .getResponse().getCookie("refresh_token").getValue();
        assertThat(first).isNotEqualTo(second);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isEqualTo(2);
    }

    @Test
    void returnsSameErrorForUnknownWrongPasswordAndSuspendedUser() throws Exception {
        signup("member@example.com", "password123");
        for (var request : new String[][] {
                {"unknown@example.com", "password123"},
                {"member@example.com", "wrong-password"}
        }) {
            mockMvc.perform(login(request[0], request[1]))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호를 확인해 주세요."));
        }
        jdbcTemplate.update("UPDATE users SET status='SUSPENDED'");
        mockMvc.perform(login("member@example.com", "password123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void doesNotReturnTokensWhenSessionStorageFails() throws Exception {
        signup("member@example.com", "password123");
        jdbcTemplate.execute("CREATE FUNCTION reject_auth_session_insert() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'forced'; END; $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE TRIGGER reject_auth_session_insert BEFORE INSERT ON auth_sessions FOR EACH ROW EXECUTE FUNCTION reject_auth_session_insert()");
        var response = mockMvc.perform(login("member@example.com", "password123"))
                .andExpect(status().isInternalServerError()).andReturn().getResponse();
        assertThat(response.getCookie("refresh_token")).isNull();
        assertThat(response.getContentAsString()).doesNotContain("accessToken");
    }

    @Test void publishesLoginContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401']").exists());
    }

    private void signup(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","profileId":"open_seed"}
                """.formatted(email, password))).andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    private String sha256(String value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
