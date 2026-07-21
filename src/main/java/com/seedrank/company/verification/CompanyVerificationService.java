package com.seedrank.company.verification;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.company.profile.CompanyProfileRepository;

@Service
class CompanyVerificationService {
    private final AccessTokenAuthenticator authenticator;
    private final CompanyProfileRepository profiles;
    private final CompanyVerificationRepository verifications;
    private final CompanyVerificationToken tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration expiresIn;

    CompanyVerificationService(
            AccessTokenAuthenticator authenticator,
            CompanyProfileRepository profiles,
            CompanyVerificationRepository verifications,
            CompanyVerificationToken tokens,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${app.company-verification.expires-in:PT30M}") Duration expiresIn) {
        if (expiresIn.isZero() || expiresIn.isNegative()) {
            throw new IllegalArgumentException("Company verification expiry must be positive");
        }
        this.authenticator = authenticator;
        this.profiles = profiles;
        this.verifications = verifications;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
        this.expiresIn = expiresIn;
    }

    @Transactional
    CompanyVerificationResponse send(String authorization) {
        var principal = authenticator.authenticate(authorization);
        var profile = profiles.findByUserIdForUpdate(principal.userId())
                .orElseThrow(CompanyProfileRequiredException::new);
        if (profile.getVerifiedAt() != null) {
            throw new CompanyAlreadyVerifiedException();
        }

        var now = clock.instant();
        verifications.findActiveByProfileId(profile.getId())
                .forEach(verification -> verification.invalidate(now));
        verifications.flush();

        var token = tokens.issue();
        var expiresAt = now.plus(expiresIn);
        verifications.save(CompanyVerification.issue(profile, token.hash(), expiresAt, now));
        events.publishEvent(new CompanyVerificationRequested(profile.getCompanyEmail(), token.raw(), expiresAt));
        return new CompanyVerificationResponse(expiresAt);
    }
}
