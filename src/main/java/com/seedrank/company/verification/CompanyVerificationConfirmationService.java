package com.seedrank.company.verification;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompanyVerificationConfirmationService {
    private final CompanyVerificationRepository verifications;
    private final CompanyVerificationToken tokens;
    private final Clock clock;

    CompanyVerificationConfirmationService(
            CompanyVerificationRepository verifications,
            CompanyVerificationToken tokens,
            Clock clock) {
        this.verifications = verifications;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    void confirm(String rawToken) {
        var verification = verifications.findByTokenHashForUpdate(tokens.hash(rawToken))
                .orElseThrow(InvalidCompanyVerificationTokenException::new);
        verification.confirm(clock.instant());
    }
}
