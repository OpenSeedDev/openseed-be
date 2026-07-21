package com.seedrank.member.me;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.auth.login.InvalidAccessTokenException;
import com.seedrank.company.profile.CompanyProfileRepository;
import com.seedrank.member.UserRepository;

@Service
class MyAccountService {
    private final AccessTokenAuthenticator authenticator;
    private final UserRepository users;
    private final CompanyProfileRepository companyProfiles;

    MyAccountService(
            AccessTokenAuthenticator authenticator,
            UserRepository users,
            CompanyProfileRepository companyProfiles) {
        this.authenticator = authenticator;
        this.users = users;
        this.companyProfiles = companyProfiles;
    }

    @Transactional(readOnly = true)
    MyAccountResponse get(String authorization) {
        var principal = authenticator.authenticate(authorization);
        var user = users.findById(principal.userId()).orElseThrow(InvalidAccessTokenException::new);
        CompanyVerificationStatus verificationStatus = companyProfiles.existsByUserId(user.getId())
                ? CompanyVerificationStatus.PENDING
                : CompanyVerificationStatus.NOT_STARTED;
        return MyAccountResponse.from(user, verificationStatus);
    }
}
