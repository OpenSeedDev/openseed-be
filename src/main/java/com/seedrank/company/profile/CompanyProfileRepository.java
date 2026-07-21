package com.seedrank.company.profile;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, UUID> {
    boolean existsByUserId(UUID userId);

    Optional<CompanyProfile> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from CompanyProfile profile where profile.user.id = :userId")
    Optional<CompanyProfile> findByUserIdForUpdate(@Param("userId") UUID userId);
}
