package com.seedrank.unit.recovery.payout;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PendingRecoveryPayoutRepository extends JpaRepository<PendingRecoveryPayout, UUID> {
    @Query("""
            select coalesce(sum(payout.paidAmount), 0) from PendingRecoveryPayout payout
            where payout.user.id = :userId
              and payout.paidAt >= :fromInclusive
              and payout.paidAt < :toExclusive
            """)
    long sumPaidAmount(
            @Param("userId") UUID userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query(value = """
            select coalesce(sum(wallet_paid_amount), 0) from seed_unit_recoveries
            where user_id = :userId
              and created_at >= :fromInclusive
              and created_at < :toExclusive
            """, nativeQuery = true)
    long sumInitialRecoveryPaidAmount(
            @Param("userId") UUID userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
