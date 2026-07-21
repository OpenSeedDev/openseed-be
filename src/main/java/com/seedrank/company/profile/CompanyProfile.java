package com.seedrank.company.profile;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.member.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_profiles")
public class CompanyProfile {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "company_email", nullable = false, length = 254, unique = true)
    private String companyEmail;

    @Column(name = "company_domain", nullable = false, length = 253)
    private String companyDomain;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyProfile() {
    }

    private CompanyProfile(User user, String companyName, NormalizedCompanyEmail email, Instant now) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.companyName = companyName;
        this.companyEmail = email.value();
        this.companyDomain = email.domain();
        this.createdAt = now;
        this.updatedAt = now;
    }

    static CompanyProfile create(User user, String companyName, NormalizedCompanyEmail email, Instant now) {
        return new CompanyProfile(user, companyName, email, now);
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCompanyDomain() {
        return companyDomain;
    }

    public String getCompanyEmail() {
        return companyEmail;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void verify(Instant now) {
        if (verifiedAt != null) {
            throw new IllegalStateException("Company profile is already verified");
        }
        verifiedAt = now;
        updatedAt = now;
        user.promoteToCompany(now);
    }
}
