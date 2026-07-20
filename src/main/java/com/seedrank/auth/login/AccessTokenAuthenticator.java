package com.seedrank.auth.login;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.seedrank.member.User;

@Component
public class AccessTokenAuthenticator {
    private static final String BEARER_PREFIX = "Bearer ";
    private final TokenIssuer tokens;
    private final AuthSessionRepository sessions;
    private final Clock clock;

    AccessTokenAuthenticator(TokenIssuer tokens, AuthSessionRepository sessions, Clock clock) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.clock = clock;
    }

    public Principal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new InvalidAccessTokenException();
        }
        String rawToken = authorization.substring(BEARER_PREFIX.length());
        if (rawToken.isBlank() || !rawToken.equals(rawToken.strip())) {
            throw new InvalidAccessTokenException();
        }
        var claims = tokens.verifyAccess(rawToken, clock.instant());
        var session = sessions.findByIdWithUser(claims.sessionId()).orElseThrow(InvalidAccessTokenException::new);
        User user = session.user();
        if (session.revoked() || !session.expiresAt().isAfter(clock.instant())
                || !user.getId().equals(claims.userId()) || user.getStatus() != User.Status.ACTIVE) {
            throw new InvalidAccessTokenException();
        }
        return new Principal(user.getId(), session.id());
    }

    public record Principal(UUID userId, UUID sessionId) {
    }
}
