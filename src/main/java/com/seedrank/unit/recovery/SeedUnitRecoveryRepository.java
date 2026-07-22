package com.seedrank.unit.recovery;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SeedUnitRecoveryRepository extends JpaRepository<SeedUnitRecovery, UUID> {
    Optional<SeedUnitRecovery> findByLotId(UUID lotId);

    @Query("""
            select coalesce(sum(recovery.walletPaidAmount), 0) from SeedUnitRecovery recovery
            where recovery.user.id = :userId
              and recovery.createdAt >= :fromInclusive
              and recovery.createdAt < :toExclusive
            """)
    long sumWalletPaidAmount(
            @Param("userId") UUID userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
