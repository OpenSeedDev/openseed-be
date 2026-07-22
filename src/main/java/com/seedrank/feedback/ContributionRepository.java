package com.seedrank.feedback;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {
}
