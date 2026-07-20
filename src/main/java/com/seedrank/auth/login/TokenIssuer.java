package com.seedrank.auth.login;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedrank.member.User;

@Component
class TokenIssuer {
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();
    TokenIssuer(@Value("${app.auth.jwt-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
    }
    String access(User user, UUID sessionId, Instant now) {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"sub\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d,\"jti\":\"%s\",\"sid\":\"%s\"}"
                .formatted(user.getId(), user.getRole(), now.getEpochSecond(), now.plusSeconds(900).getEpochSecond(), UUID.randomUUID(), sessionId))
                .getBytes(StandardCharsets.UTF_8));
        String content = header + "." + payload;
        return content + "." + b64(sign(content));
    }

    AccessClaims verifyAccess(String token, Instant now) {
        try {
            if (token==null) throw new InvalidAccessTokenException();
            String[] parts=token.split("\\.", -1);
            if (parts.length!=3) throw new InvalidAccessTokenException();
            String content=parts[0]+"."+parts[1];
            byte[] supplied=Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(content), supplied)) throw new InvalidAccessTokenException();
            JsonNode header=json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode payload=json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(text(header, "alg")) || !"JWT".equals(text(header, "typ"))) throw new InvalidAccessTokenException();
            UUID userId=UUID.fromString(text(payload, "sub"));
            UUID sessionId=UUID.fromString(text(payload, "sid"));
            long expiresAt=payload.path("exp").asLong(Long.MIN_VALUE);
            long issuedAt=payload.path("iat").asLong(Long.MAX_VALUE);
            if (expiresAt==Long.MIN_VALUE || issuedAt==Long.MAX_VALUE || expiresAt<=now.getEpochSecond() || issuedAt>now.plusSeconds(30).getEpochSecond()) {
                throw new InvalidAccessTokenException();
            }
            return new AccessClaims(userId, sessionId);
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidAccessTokenException();
        }
    }

    String refresh() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return b64(bytes); }
    String hash(String token) { try { return b64(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); } catch (GeneralSecurityException e) { throw new IllegalStateException(e); } }
    private byte[] sign(String content) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return mac.doFinal(content.getBytes(StandardCharsets.UTF_8)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private String text(JsonNode node, String field) {
        JsonNode value=node.get(field); if (value==null || !value.isTextual() || value.asText().isBlank()) throw new InvalidAccessTokenException(); return value.asText();
    }
    private String b64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    record AccessClaims(UUID userId, UUID sessionId) {}
}
