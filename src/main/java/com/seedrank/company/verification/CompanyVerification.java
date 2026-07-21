package com.seedrank.company.verification;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.company.profile.CompanyProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_verifications")
class CompanyVerification {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_profile_id", nullable = false)
    private CompanyProfile companyProfile;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CompanyVerification() {
    }

    private CompanyVerification(CompanyProfile companyProfile, String tokenHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.companyProfile = companyProfile;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    static CompanyVerification issue(
            CompanyProfile companyProfile,
            String tokenHash,
            Instant expiresAt,
            Instant now) {
        return new CompanyVerification(companyProfile, tokenHash, expiresAt, now);
    }

    void invalidate(Instant now) {
        if (usedAt == null && invalidatedAt == null) {
            invalidatedAt = now;
        }
    }

    CompanyProfile confirm(Instant now) {
        if (usedAt != null || invalidatedAt != null || !expiresAt.isAfter(now)
                || companyProfile.getVerifiedAt() != null) {
            throw new InvalidCompanyVerificationTokenException();
        }
        usedAt = now;
        companyProfile.verify(now);
        return companyProfile;
    }
}
