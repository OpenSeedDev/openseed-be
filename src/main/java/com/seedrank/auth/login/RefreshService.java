package com.seedrank.auth.login;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.seedrank.member.User;
import com.seedrank.member.UserRepository;

@Service
class RefreshService {
    private final AuthSessionRepository sessions; private final UserRepository users; private final TokenIssuer tokens; private final Clock clock;
    RefreshService(AuthSessionRepository sessions, UserRepository users, TokenIssuer tokens, Clock clock) {
        this.sessions=sessions; this.users=users; this.tokens=tokens; this.clock=clock;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    Result refresh(String rawToken) {
        if (rawToken==null || rawToken.isBlank()) throw new InvalidRefreshTokenException();
        var now=clock.instant();
        String hash=tokens.hash(rawToken);
        var userId=sessions.findUserIdByHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        users.findByIdForUpdate(userId).orElseThrow(InvalidRefreshTokenException::new);
        var current=sessions.findByHashForUpdate(hash).orElseThrow(InvalidRefreshTokenException::new);
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
        var next=sessions.saveAndFlush(current.rotateTo(tokens.hash(refresh), now));
        return new Result(new RefreshResponse(tokens.access(current.user(), next.id(), now), "Bearer", 900), refresh);
    }
    record Result(RefreshResponse response, String refreshToken) {}
}
