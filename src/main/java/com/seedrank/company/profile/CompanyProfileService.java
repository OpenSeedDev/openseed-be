package com.seedrank.company.profile;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.auth.login.InvalidAccessTokenException;
import com.seedrank.member.UserRepository;

@Service
class CompanyProfileService {
    private final AccessTokenAuthenticator authenticator;
    private final UserRepository users;
    private final CompanyProfileRepository profiles;
    private final CompanyEmailPolicy emails;
    private final Clock clock;

    CompanyProfileService(
            AccessTokenAuthenticator authenticator,
            UserRepository users,
            CompanyProfileRepository profiles,
            CompanyEmailPolicy emails,
            Clock clock) {
        this.authenticator = authenticator;
        this.users = users;
        this.profiles = profiles;
        this.emails = emails;
        this.clock = clock;
    }

    @Transactional
    CompanyProfileResponse create(String authorization, CompanyProfileRequest request) {
        var principal = authenticator.authenticate(authorization);
        var user = users.findById(principal.userId()).orElseThrow(InvalidAccessTokenException::new);
        String companyName = request.companyName().strip();
        if (companyName.isEmpty()) {
            throw new IllegalArgumentException("companyName");
        }
        var email = emails.normalize(request.companyEmail());
        if (profiles.existsByUserId(user.getId())) {
            throw new CompanyProfileAlreadyExistsException();
        }
        try {
            return CompanyProfileResponse.from(
                    profiles.saveAndFlush(CompanyProfile.create(user, companyName, email, clock.instant())));
        } catch (DataIntegrityViolationException exception) {
            throw new CompanyProfileAlreadyExistsException();
        }
    }
}
