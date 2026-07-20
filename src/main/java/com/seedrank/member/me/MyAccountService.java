package com.seedrank.member.me;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.auth.login.InvalidAccessTokenException;
import com.seedrank.member.UserRepository;

@Service
class MyAccountService {
    private final AccessTokenAuthenticator authenticator;
    private final UserRepository users;

    MyAccountService(AccessTokenAuthenticator authenticator, UserRepository users) {
        this.authenticator = authenticator;
        this.users = users;
    }

    @Transactional(readOnly = true)
    MyAccountResponse get(String authorization) {
        var principal = authenticator.authenticate(authorization);
        var user = users.findById(principal.userId()).orElseThrow(InvalidAccessTokenException::new);
        return MyAccountResponse.from(user);
    }
}
