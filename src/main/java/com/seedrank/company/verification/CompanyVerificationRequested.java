package com.seedrank.company.verification;

import java.time.Instant;

record CompanyVerificationRequested(String companyEmail, String rawToken, Instant expiresAt) {
}
