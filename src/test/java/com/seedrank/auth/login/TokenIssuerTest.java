package com.seedrank.auth.login;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import com.seedrank.member.User;

class TokenIssuerTest {
    @Test void jwtContainsOnlyApprovedIdentityClaims() throws Exception {
        var issuer = new TokenIssuer("test-signing-key-with-at-least-32-bytes");
        var user = User.create("private@example.com", "hash", "private_profile", Instant.EPOCH);
        var sessionId = java.util.UUID.randomUUID();
        String token = issuer.access(user, sessionId, Instant.ofEpochSecond(1000));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(payload).contains(user.getId().toString(), sessionId.toString(), "\"role\":\"USER\"", "\"iat\":1000", "\"exp\":1900", "\"jti\"", "\"sid\"")
                .doesNotContain("private@example.com", "private_profile");
        String signed = token.substring(0, token.lastIndexOf('.'));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-signing-key-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertThat(token.split("\\.")[2]).isEqualTo(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void rejectsShortSigningSecret() {
        assertThatThrownBy(() -> new TokenIssuer("too-short")).isInstanceOf(IllegalArgumentException.class);
    }
}
