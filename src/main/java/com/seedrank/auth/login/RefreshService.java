package com.seedrank.auth.login;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.seedrank.member.User;

@Service
class RefreshService {
    private final AuthSessionRepository sessions; private final TokenIssuer tokens; private final Clock clock;
    RefreshService(AuthSessionRepository sessions, TokenIssuer tokens, Clock clock) { this.sessions=sessions; this.tokens=tokens; this.clock=clock; }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    Result refresh(String rawToken) {
        if (rawToken==null || rawToken.isBlank()) throw new InvalidRefreshTokenException();
        var now=clock.instant();
        var current=sessions.findByHashForUpdate(tokens.hash(rawToken)).orElseThrow(InvalidRefreshTokenException::new);
        if (current.revoked()) {
            sessions.revokeFamily(current.familyId(), now, "REUSE_DETECTED");
            throw new InvalidRefreshTokenException();
        }
        if (!current.expiresAt().isAfter(now)) throw new InvalidRefreshTokenException();
        if (current.user().getStatus()!=User.Status.ACTIVE) {
            sessions.revokeFamily(current.familyId(), now, "USER_SUSPENDED");
            throw new InvalidRefreshTokenException();
        }
        String refresh=tokens.refresh();
        sessions.saveAndFlush(current.rotateTo(tokens.hash(refresh), now));
        return new Result(new RefreshResponse(tokens.access(current.user(), now), "Bearer", 900), refresh);
    }
    record Result(RefreshResponse response, String refreshToken) {}
}
