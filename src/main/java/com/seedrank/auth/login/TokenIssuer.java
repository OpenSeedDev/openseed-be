package com.seedrank.auth.login;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.seedrank.member.User;

@Component
class TokenIssuer {
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();
    TokenIssuer(@Value("${app.auth.jwt-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
    }
    String access(User user, Instant now) {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"sub\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}"
                .formatted(user.getId(), user.getRole(), now.getEpochSecond(), now.plusSeconds(900).getEpochSecond(), UUID.randomUUID()))
                .getBytes(StandardCharsets.UTF_8));
        String content = header + "." + payload;
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return content + "." + b64(mac.doFinal(content.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    String refresh() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return b64(bytes); }
    String hash(String token) { try { return b64(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); } catch (GeneralSecurityException e) { throw new IllegalStateException(e); } }
    private String b64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
