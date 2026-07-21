package com.seedrank.member.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class UpdateProfileIdIntegrationTest {

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
    void updatesTheCurrentProfileIdWithoutChangingTheInternalUserId() throws Exception {
        signup("member@example.com", "before_id");
        String accessToken = login("member@example.com");
        UUID userId = userId("member@example.com");

        mockMvc.perform(update(accessToken, "after_id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.profileId").value("after_id"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.profileId").value("after_id"));
        assertThat(userId("member@example.com")).isEqualTo(userId);
    }

    @Test
    void allowsDuplicateProfileIdsAndChangesOnlyTheAuthenticatedUser() throws Exception {
        signup("first@example.com", "first_id");
        signup("second@example.com", "shared_id");
        String firstAccessToken = login("first@example.com");
        UUID firstId = userId("first@example.com");
        UUID secondId = userId("second@example.com");

        mockMvc.perform(update(firstAccessToken, "shared_id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(firstId.toString()))
                .andExpect(jsonPath("$.profileId").value("shared_id"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE profile_id='shared_id'", Integer.class)).isEqualTo(2);
        assertThat(userId("second@example.com")).isEqualTo(secondId);
    }

    @Test
    void allowsRepeatedChangesWithoutAChangeLimit() throws Exception {
        signup("member@example.com", "start_id");
        String accessToken = login("member@example.com");

        for (int index = 0; index < 25; index++) {
            mockMvc.perform(update(accessToken, "profile_" + index))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileId").value("profile_" + index));
        }

        assertThat(profileId("member@example.com")).isEqualTo("profile_24");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "abcdefghijklmnopqrstu", "한글아이디", "white space", "dot.name", "hyphen-name"})
    void rejectsProfileIdsOutsideTheApprovedFormat(String profileId) throws Exception {
        signup("member@example.com", "before_id");
        String accessToken = login("member@example.com");

        mockMvc.perform(update(accessToken, profileId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE_ID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("profileId"));

        assertThat(profileId("member@example.com")).isEqualTo("before_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "administrator", "root", "system", "support", "seedrank", "openseed"})
    void rejectsReservedProfileIdsIgnoringCase(String profileId) throws Exception {
        signup("member@example.com", "before_id");
        String accessToken = login("member@example.com");

        mockMvc.perform(update(accessToken, profileId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE_ID"));

        assertThat(profileId("member@example.com")).isEqualTo("before_id");
    }

    @Test
    void rejectsAMissingProfileId() throws Exception {
        signup("member@example.com", "before_id");
        String accessToken = login("member@example.com");

        mockMvc.perform(patch("/api/v1/me/profile-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE_ID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("profileId"));

        assertThat(profileId("member@example.com")).isEqualTo("before_id");
    }

    @Test
    void rejectsMissingAndRevokedAuthentication() throws Exception {
        signup("member@example.com", "before_id");
        Login login = loginWithRefresh("member@example.com");

        mockMvc.perform(patch("/api/v1/me/profile-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"after_id\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new org.springframework.mock.web.MockCookie("refresh_token", login.refreshToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(update(login.accessToken(), "after_id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        assertThat(profileId("member@example.com")).isEqualTo("before_id");
    }

    @Test
    void doesNotExposeSensitiveAccountOrSessionFields() throws Exception {
        signup("member@example.com", "before_id");
        String accessToken = login("member@example.com");

        String response = mockMvc.perform(update(accessToken, "after_id"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(
                "member@example.com", "password", "accessToken", "refreshToken", "sessionId");
        assertThat(objectMapper.readTree(response).size()).isEqualTo(3);
    }

    @Test
    void publishesTheUpdateProfileIdOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile-id'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile-id'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile-id'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile-id'].patch.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateProfileIdRequest.properties.profileId").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateProfileIdResponse.properties.userId").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateProfileIdResponse.properties.profileId").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateProfileIdResponse.properties.updatedAt").exists());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder update(
            String accessToken, String profileId) {
        return patch("/api/v1/me/profile-id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + profileId + "\"}");
    }

    private void signup(String email, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","profileId":"%s"}
                                """.formatted(email, profileId)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        return loginWithRefresh(email).accessToken();
    }

    private Login loginWithRefresh(String email) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Login(
                objectMapper.readTree(response.getContentAsString()).get("accessToken").asText(),
                response.getCookie("refresh_token").getValue());
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
    }

    private String profileId(String email) {
        return jdbc.queryForObject("SELECT profile_id FROM users WHERE email=?", String.class, email);
    }

    private record Login(String accessToken, String refreshToken) {
    }
}
