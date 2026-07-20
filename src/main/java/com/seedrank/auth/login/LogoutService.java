package com.seedrank.auth.login;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.member.UserRepository;

@Service
class LogoutService {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final TokenIssuer tokens;
    private final AccessTokenAuthenticator accessTokens;
    private final Clock clock;

    LogoutService(AuthSessionRepository sessions, UserRepository users, TokenIssuer tokens,
            AccessTokenAuthenticator accessTokens, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Transactional
    void logoutCurrent(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = tokens.hash(rawRefreshToken);
        var userId = sessions.findUserIdByHash(hash);
        if (userId.isEmpty()) {
            return;
        }
        users.findByIdForUpdate(userId.get()).ifPresent(user ->
                sessions.findByHashForUpdate(hash).ifPresent(current ->
                        sessions.revokeFamily(current.familyId(), clock.instant(), "LOGOUT")));
    }

    @Transactional
    void logoutAll(String authorization) {
        var principal = accessTokens.authenticate(authorization);
        users.findByIdForUpdate(principal.userId()).orElseThrow(InvalidAccessTokenException::new);
        sessions.revokeAllByUser(principal.userId(), clock.instant(), "LOGOUT_ALL");
    }
}
