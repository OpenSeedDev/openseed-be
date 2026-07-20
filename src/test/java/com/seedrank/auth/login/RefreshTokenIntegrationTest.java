package com.seedrank.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.auth.cookie-secure=false",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }
    @AfterEach void triggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_rotated_session_insert ON auth_sessions");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_rotated_session_insert()");
    }

    @Test void rotatesRefreshTokenAndSession() throws Exception {
        String oldToken = login();
        var result = mockMvc.perform(refresh(oldToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(cookie().httpOnly("refresh_token", true)).andReturn();
        String newToken = result.getResponse().getCookie("refresh_token").getValue();
        assertThat(newToken).isNotEqualTo(oldToken);
        var rows = jdbc.queryForList("SELECT family_id, rotated_from_id, revocation_reason, revoked_at FROM auth_sessions ORDER BY created_at");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("revocation_reason")).isEqualTo("ROTATED");
        assertThat(rows.get(1).get("family_id")).isEqualTo(rows.get(0).get("family_id"));
        assertThat(rows.get(1).get("rotated_from_id")).isNotNull();
        Long lifetime=jdbc.queryForObject("SELECT extract(epoch FROM (expires_at-created_at))::bigint FROM auth_sessions WHERE rotated_from_id IS NOT NULL", Long.class);
        assertThat(lifetime).isEqualTo(14*24*60*60L);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(newToken, "refreshToken");
    }

    @Test void replayRevokesTheWholeFamily() throws Exception {
        String oldToken = login();
        String current = mockMvc.perform(refresh(oldToken)).andReturn().getResponse().getCookie("refresh_token").getValue();
        mockMvc.perform(refresh(oldToken)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(cookie().maxAge("refresh_token", 0));
        mockMvc.perform(refresh(current)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE revoked_at IS NULL", Integer.class)).isZero();
    }

    @Test void rejectsMissingInvalidExpiredAndSuspendedSessions() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh("invalid-token")).andExpect(status().isUnauthorized());
        String expired = login();
        jdbc.update("UPDATE auth_sessions SET expires_at=now()-interval '1 second'");
        mockMvc.perform(refresh(expired)).andExpect(status().isUnauthorized());
        clean();
        String suspended = login();
        jdbc.update("UPDATE users SET status='SUSPENDED'");
        mockMvc.perform(refresh(suspended)).andExpect(status().isUnauthorized());
    }

    @Test void rollsBackRotationWhenNewSessionInsertFails() throws Exception {
        String token = login();
        jdbc.execute("CREATE FUNCTION reject_rotated_session_insert() RETURNS trigger AS $$ BEGIN IF NEW.rotated_from_id IS NOT NULL THEN RAISE EXCEPTION 'forced'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER reject_rotated_session_insert BEFORE INSERT ON auth_sessions FOR EACH ROW EXECUTE FUNCTION reject_rotated_session_insert()");
        mockMvc.perform(refresh(token)).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM auth_sessions", Boolean.class)).isTrue();
    }

    @Test void concurrentRefreshAllowsOneResponseButCompromisesFamily() throws Exception {
        String token = login();
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> { ready.countDown(); start.await(5, TimeUnit.SECONDS); return mockMvc.perform(refresh(token)).andReturn().getResponse().getStatus(); };
            var a=executor.submit(task); var b=executor.submit(task); ready.await(5, TimeUnit.SECONDS); start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 401);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE revoked_at IS NULL", Integer.class)).isZero();
    }

    @Test void publishesRefreshContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['401']").exists());
    }

    private String login() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"password\":\"password123\",\"profileId\":\"open_seed\"}"))
                .andExpect(status().isCreated());
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token").getValue();
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String token) {
        return post("/api/v1/auth/refresh").cookie(new MockCookie("refresh_token", token));
    }
}
