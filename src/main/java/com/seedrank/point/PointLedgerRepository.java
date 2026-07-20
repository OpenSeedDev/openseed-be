package com.seedrank.point;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointLedgerRepository extends JpaRepository<PointLedger, UUID> {
}
