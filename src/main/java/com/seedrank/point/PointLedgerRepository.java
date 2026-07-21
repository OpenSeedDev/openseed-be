package com.seedrank.point;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointLedgerRepository extends JpaRepository<PointLedger, UUID> {

    Optional<PointLedger> findBySourceTypeAndSourceId(
            PointLedger.SourceType sourceType,
            UUID sourceId);

    @Query("""
            select coalesce(sum(ledger.paidAmount), 0) from PointLedger ledger
            where ledger.user.id = :userId and ledger.policyDate = :policyDate
            """)
    long sumPaidActivityRewards(
            @Param("userId") UUID userId,
            @Param("policyDate") LocalDate policyDate);

    @Query("""
            select count(ledger) from PointLedger ledger
            where ledger.user.id = :userId
              and ledger.policyDate = :policyDate
              and ledger.sourceType = :sourceType
              and ledger.paidAmount > 0
            """)
    long countPaidRewards(
            @Param("userId") UUID userId,
            @Param("policyDate") LocalDate policyDate,
            @Param("sourceType") PointLedger.SourceType sourceType);

    @Query("""
            select coalesce(sum(ledger.paidAmount), 0) from PointLedger ledger
            where ledger.user.id = :userId
              and ledger.policyDate = :policyDate
              and ledger.sourceType = :sourceType
            """)
    long sumUnitPurchasePrincipal(
            @Param("userId") UUID userId,
            @Param("policyDate") LocalDate policyDate,
            @Param("sourceType") PointLedger.SourceType sourceType);

    @Query("""
            select ledger from PointLedger ledger
            where ledger.user.id = :userId
            order by ledger.createdAt desc, ledger.id desc
            """)
    List<PointLedger> findFirstPage(
            @Param("userId") UUID userId,
            Pageable pageable);

    @Query("""
            select ledger from PointLedger ledger
            where ledger.user.id = :userId
              and (ledger.createdAt < :cursorAt
                or (ledger.createdAt = :cursorAt and ledger.id < :cursorId))
            order by ledger.createdAt desc, ledger.id desc
            """)
    List<PointLedger> findPageAfter(
            @Param("userId") UUID userId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
