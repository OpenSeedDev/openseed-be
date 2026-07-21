package com.seedrank.company.profile;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, UUID> {
    boolean existsByUserId(UUID userId);
}
