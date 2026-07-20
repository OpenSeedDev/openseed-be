package com.seedrank.auth.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.seedrank.TestcontainersConfiguration;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SignupIntegrationTest {

    private static final String SIGNUP_PATH = "/api/v1/auth/signup";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM point_ledgers");
        jdbcTemplate.update("DELETE FROM point_wallets");
        jdbcTemplate.update("DELETE FROM users");
    }

    @AfterEach
    void removeFailureTriggers() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_wallet_insert ON point_wallets");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_ledger_insert ON point_ledgers");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_signup_insert()");
    }

    @Test
    void signsUpAnActiveUserWithWalletAndSignupLedger() throws Exception {
        mockMvc.perform(signup("  Member@Example.COM  ", "password123", "open_seed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.profileId").value("open_seed"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.pointBalance").value(300))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        var user = jdbcTemplate.queryForMap(
                "SELECT email, password_hash, profile_id, role, status FROM users");
        assertThat(user.get("email")).isEqualTo("member@example.com");
        assertThat(user.get("password_hash")).asString()
                .isNotEqualTo("password123")
                .startsWith("$2");
        assertThat(user.get("profile_id")).isEqualTo("open_seed");
        assertThat(user.get("role")).isEqualTo("USER");
        assertThat(user.get("status")).isEqualTo("ACTIVE");

        var wallet = jdbcTemplate.queryForMap(
                "SELECT balance, pending_recovery_balance FROM point_wallets");
        assertThat(wallet.get("balance")).isEqualTo(300);
        assertThat(wallet.get("pending_recovery_balance")).isEqualTo(0);

        var ledger = jdbcTemplate.queryForMap("""
                SELECT type, original_amount, paid_amount, expired_amount, balance_after, source_type
                FROM point_ledgers
                """);
        assertThat(ledger.get("type")).isEqualTo("CREDIT");
        assertThat(ledger.get("original_amount")).isEqualTo(300);
        assertThat(ledger.get("paid_amount")).isEqualTo(300);
        assertThat(ledger.get("expired_amount")).isEqualTo(0);
        assertThat(ledger.get("balance_after")).isEqualTo(300);
        assertThat(ledger.get("source_type")).isEqualTo("SIGNUP_BONUS");
    }

    @Test
    void allowsDuplicateProfileIdsAndProfileIdBoundaries() throws Exception {
        mockMvc.perform(signup("first@example.com", "password123", "abc"))
                .andExpect(status().isCreated());
        mockMvc.perform(signup("second@example.com", "password123", "abcdefghijklmnopqrst"))
                .andExpect(status().isCreated());
        mockMvc.perform(signup("third@example.com", "password123", "abc"))
                .andExpect(status().isCreated());

        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(userCount).isEqualTo(3);
    }

    @Test
    void rejectsInvalidEmailAndPasswordBoundaries() throws Exception {
        mockMvc.perform(signup("invalid-email", "password123", "valid_id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(signup("member@example.com", "short7", "valid_id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String tooLongPassword = "가".repeat(25);
        assertThat(tooLongPassword.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(72);
        mockMvc.perform(signup("member@example.com", tooLongPassword, "valid_id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingFieldsAndAnOversizedEmail() throws Exception {
        mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE_ID"));

        String oversizedEmail = "a".repeat(243) + "@example.com";
        assertThat(oversizedEmail).hasSizeGreaterThan(254);
        mockMvc.perform(signup(oversizedEmail, "password123", "valid_id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidAndReservedProfileIds() throws Exception {
        for (String profileId : List.of("ab", "abcdefghijklmnopqrstu", "open-seed", "ADMIN", "OpenSeed")) {
            mockMvc.perform(signup("member-%s@example.com".formatted(profileId), "password123", profileId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PROFILE_ID"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
    }

    @Test
    void rejectsAnEmailThatOnlyDiffersByCaseAndWhitespace() throws Exception {
        mockMvc.perform(signup("member@example.com", "password123", "first_id"))
                .andExpect(status().isCreated());

        mockMvc.perform(signup("  MEMBER@EXAMPLE.COM ", "password456", "second_id"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void allowsOnlyOneOfTwoConcurrentSignupsWithTheSameEmail() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> signup = () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            MvcResult result = mockMvc.perform(
                            signup("race@example.com", "password123", "same_profile"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(signup);
            Future<Integer> second = executor.submit(signup);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(201, 409);
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM point_wallets", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM point_ledgers", Integer.class)).isEqualTo(1);
    }

    @Test
    void rollsBackEverythingWhenWalletCreationFails() throws Exception {
        installRejectInsertTrigger("point_wallets", "reject_wallet_insert");

        mockMvc.perform(signup("member@example.com", "password123", "open_seed"))
                .andExpect(status().isInternalServerError());

        assertSignupTablesAreEmpty();
    }

    @Test
    void rollsBackEverythingWhenLedgerCreationFails() throws Exception {
        installRejectInsertTrigger("point_ledgers", "reject_ledger_insert");

        mockMvc.perform(signup("member@example.com", "password123", "open_seed"))
                .andExpect(status().isInternalServerError());

        assertSignupTablesAreEmpty();
    }

    @Test
    void doesNotLogPasswordOrHash(CapturedOutput output) throws Exception {
        String secret = "never-log-this-password";

        mockMvc.perform(signup("member@example.com", secret, "open_seed"))
                .andExpect(status().isCreated());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users", String.class);
        assertThat(output).doesNotContain(secret).doesNotContain(storedHash);
    }

    @Test
    void publishesTheSignupContractInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['409']").exists());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(
            String email, String password, String profileId) {
        String body = """
                {
                  "email": "%s",
                  "password": "%s",
                  "profileId": "%s"
                }
                """.formatted(email, password, profileId);
        return post(SIGNUP_PATH).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void installRejectInsertTrigger(String table, String trigger) {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_signup_insert()
                RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced signup insert failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("CREATE TRIGGER %s BEFORE INSERT ON %s "
                .formatted(trigger, table)
                + "FOR EACH ROW EXECUTE FUNCTION reject_signup_insert()");
    }

    private void assertSignupTablesAreEmpty() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM point_wallets", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM point_ledgers", Integer.class)).isZero();
    }
}
