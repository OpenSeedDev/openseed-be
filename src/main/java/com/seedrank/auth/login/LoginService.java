package com.seedrank.auth.login;

import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.seedrank.member.User;
import com.seedrank.member.UserRepository;

@Service
class LoginService {
    private final UserRepository users; private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords; private final TokenIssuer tokens; private final Clock clock;
    private final String dummyPasswordHash;
    LoginService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder passwords, TokenIssuer tokens, Clock clock) {
        this.users=users; this.sessions=sessions; this.passwords=passwords; this.tokens=tokens; this.clock=clock;
        this.dummyPasswordHash=passwords.encode("non-user-timing-value");
    }
    @Transactional LoginResult login(LoginRequest request) {
        if (request.email()==null || request.password()==null) throw new IllegalArgumentException("missing credentials");
        String email=request.email().strip().toLowerCase(Locale.ROOT);
        var candidate=users.findByEmail(email);
        boolean passwordMatches=passwords.matches(request.password(), candidate.map(User::getPasswordHash).orElse(dummyPasswordHash));
        User candidateUser=candidate.orElseThrow(InvalidCredentialsException::new);
        if (candidateUser.getStatus()!=User.Status.ACTIVE || !passwordMatches) throw new InvalidCredentialsException();
        User user=users.findByIdForUpdate(candidateUser.getId()).orElseThrow(InvalidCredentialsException::new);
        if (user.getStatus()!=User.Status.ACTIVE) throw new InvalidCredentialsException();
        var now=clock.instant(); String refresh=tokens.refresh();
        var session=sessions.saveAndFlush(new AuthSession(user, tokens.hash(refresh), now));
        return new LoginResult(new LoginResponse(tokens.access(user, session.id(), now), "Bearer", 900, user.getId(), user.getRole()), refresh);
    }
    record LoginResult(LoginResponse response, String refreshToken) {}
}
