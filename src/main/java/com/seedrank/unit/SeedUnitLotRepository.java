package com.seedrank.unit;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeedUnitLotRepository extends JpaRepository<SeedUnitLot, UUID> {

    Optional<SeedUnitLot> findByUserIdAndPurchaseRequestKey(UUID userId, String purchaseRequestKey);

    @Query("""
            select coalesce(sum(lot.principal), 0) from SeedUnitLot lot
            where lot.user.id = :userId and lot.idea.id = :ideaId
              and lot.status = :status
            """)
    long sumActivePrincipal(
            @Param("userId") UUID userId,
            @Param("ideaId") UUID ideaId,
            @Param("status") SeedUnitLot.Status status);

    @Query("""
            select lot from SeedUnitLot lot join fetch lot.idea
            where lot.user.id = :userId and lot.status = :status
            order by lot.purchasedAt desc, lot.id desc
            """)
    List<SeedUnitLot> findFirstOwnedPage(
            @Param("userId") UUID userId,
            @Param("status") SeedUnitLot.Status status,
            Pageable pageable);

    @Query("""
            select lot from SeedUnitLot lot join fetch lot.idea
            where lot.user.id = :userId and lot.status = :status
              and (lot.purchasedAt < :cursorAt
                or (lot.purchasedAt = :cursorAt and lot.id < :cursorId))
            order by lot.purchasedAt desc, lot.id desc
            """)
    List<SeedUnitLot> findOwnedPageAfter(
            @Param("userId") UUID userId,
            @Param("status") SeedUnitLot.Status status,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    @Query("""
            select lot.idea.id as ideaId, sum(lot.units) as units
            from SeedUnitLot lot
            where lot.user.id = :userId and lot.status = :status
            group by lot.idea.id
            """)
    List<ActiveUnitTotal> findActiveUnitTotals(
            @Param("userId") UUID userId,
            @Param("status") SeedUnitLot.Status status);

    interface ActiveUnitTotal {
        UUID getIdeaId();
        Long getUnits();
    }
}
