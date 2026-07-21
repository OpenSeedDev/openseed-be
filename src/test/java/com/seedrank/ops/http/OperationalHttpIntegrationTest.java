package com.seedrank.ops.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.seedrank.TestcontainersConfiguration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.probes.enabled=true"
})
class OperationalHttpIntegrationTest {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Autowired
    private MockMvc mockMvc;
    private Logger accessLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        accessLogger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        accessLogger.addAppender(appender);
        accessLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        accessLogger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void exposesLivenessAndReadinessHealthGroups() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void generatesRequestIdAndUsesItForHeaderAndErrorBody() throws Exception {
        MvcResult result = invalidSignup(null, "trace@example.com", "unsafe-secret");

        String requestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThatCodeIsUuid(requestId);
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + requestId + "\"");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void propagatesSafeClientRequestId() throws Exception {
        String requestId = "web-01.request:42";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .header(REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(REQUEST_ID_HEADER, requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void publishesOptionalRequestIdHeaderInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.parameters[?(@.name == 'X-Request-Id')].in")
                        .value("header"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.parameters[?(@.name == 'X-Request-Id')].required")
                        .value(false));
    }

    @Test
    void replacesUnsafeOrOversizedClientRequestId() throws Exception {
        String malicious = "bad request-id\nforged=" + "x".repeat(80);

        MvcResult result = invalidSignup(malicious, "trace@example.com", "unsafe-secret");

        String actual = result.getResponse().getHeader(REQUEST_ID_HEADER);
        assertThat(actual).isNotEqualTo(malicious);
        assertThatCodeIsUuid(actual);
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + actual + "\"");
    }

    @Test
    void writesStructuredAccessLogWithoutHeadersQueryOrBody() throws Exception {
        String requestId = "safe-trace-123";
        String email = "private-person@example.com";
        String password = "do-not-log-this-password";
        String token = "Bearer secret-access-token";

        mockMvc.perform(post("/api/v1/auth/signup?invite=private-code")
                        .header(REQUEST_ID_HEADER, requestId)
                        .header("Authorization", token)
                        .header("Cookie", "refresh_token=secret-refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","profileId":"x"}
                                """.formatted(email, password)))
                .andExpect(status().isBadRequest());

        assertThat(appender.list).hasSize(1);
        String log = appender.list.getFirst().getFormattedMessage();
        assertThat(log)
                .startsWith("{")
                .contains("\"event\":\"http_request\"")
                .contains("\"requestId\":\"" + requestId + "\"")
                .contains("\"method\":\"POST\"")
                .contains("\"route\":\"/api/v1/auth/signup\"")
                .contains("\"status\":400")
                .contains("\"durationMs\":")
                .doesNotContain("invite", "private-code", email, password, token,
                        "secret-refresh-token", "Authorization", "Cookie");
    }

    private MvcResult invalidSignup(String requestId, String email, String password) throws Exception {
        var request = post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","profileId":"x"}
                        """.formatted(email, password));
        if (requestId != null) {
            request.header(REQUEST_ID_HEADER, requestId);
        }
        return mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andReturn();
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
