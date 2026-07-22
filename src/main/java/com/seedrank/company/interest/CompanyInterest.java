package com.seedrank.company.interest;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "company_interests", uniqueConstraints = @UniqueConstraint(
        name = "uq_company_interests_idea_company",
        columnNames = {"idea_id", "company_profile_id"}))
class CompanyInterest {
    @Id
    private UUID id;

    @Column(name = "idea_id", nullable = false)
    private UUID ideaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_profile_id", nullable = false)
    private CompanyProfile companyProfile;

    @Column(name = "interested_at", nullable = false)
    private Instant interestedAt;

    protected CompanyInterest() {
    }

    private CompanyInterest(UUID ideaId, CompanyProfile companyProfile, Instant interestedAt) {
        this.id = UUID.randomUUID();
        this.ideaId = ideaId;
        this.companyProfile = companyProfile;
        this.interestedAt = interestedAt;
    }

    static CompanyInterest create(UUID ideaId, CompanyProfile companyProfile, Instant interestedAt) {
        return new CompanyInterest(ideaId, companyProfile, interestedAt);
    }

    UUID id() {
        return id;
    }

    String companyName() {
        return companyProfile.getCompanyName();
    }

    Instant interestedAt() {
        return interestedAt;
    }
}
