package com.seedrank.company.verification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CompanyVerificationRepository extends JpaRepository<CompanyVerification, UUID> {
    @Query("""
            select verification from CompanyVerification verification
            where verification.companyProfile.id = :profileId
              and verification.usedAt is null
              and verification.invalidatedAt is null
            """)
    List<CompanyVerification> findActiveByProfileId(@Param("profileId") UUID profileId);
}
