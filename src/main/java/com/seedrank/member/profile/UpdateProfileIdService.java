package com.seedrank.member.profile;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.auth.login.InvalidAccessTokenException;
import com.seedrank.member.UserRepository;

@Service
class UpdateProfileIdService {

    private final AccessTokenAuthenticator authenticator;
    private final UserRepository users;
    private final ProfileIdPolicy policy;
    private final Clock clock;

    UpdateProfileIdService(
            AccessTokenAuthenticator authenticator,
            UserRepository users,
            ProfileIdPolicy policy,
            Clock clock) {
        this.authenticator = authenticator;
        this.users = users;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    UpdateProfileIdResponse update(String authorization, UpdateProfileIdRequest request) {
        var principal = authenticator.authenticate(authorization);
        policy.validate(request == null ? null : request.profileId());
        var user = users.findByIdForUpdate(principal.userId()).orElseThrow(InvalidAccessTokenException::new);
        user.updateProfileId(request.profileId(), clock.instant());
        return UpdateProfileIdResponse.from(user);
    }
}
