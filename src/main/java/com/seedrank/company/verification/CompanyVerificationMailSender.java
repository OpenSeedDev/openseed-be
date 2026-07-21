package com.seedrank.company.verification;

import java.time.Instant;

public interface CompanyVerificationMailSender {
    void send(String companyEmail, String rawToken, Instant expiresAt);
}
