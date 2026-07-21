package com.seedrank.company.verification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

interface CompanyVerificationRepository extends JpaRepository<CompanyVerification, UUID> {
    @Query("""
            select verification from CompanyVerification verification
            where verification.companyProfile.id = :profileId
              and verification.usedAt is null
              and verification.invalidatedAt is null
            """)
    List<CompanyVerification> findActiveByProfileId(@Param("profileId") UUID profileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select verification from CompanyVerification verification
            join fetch verification.companyProfile profile
            join fetch profile.user
            where verification.tokenHash = :tokenHash
            """)
    Optional<CompanyVerification> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
