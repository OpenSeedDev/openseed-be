package com.seedrank.unit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeedUnitLotRepository extends JpaRepository<SeedUnitLot, UUID> {

    @Query("""
            select coalesce(sum(lot.principal), 0) from SeedUnitLot lot
            where lot.user.id = :userId and lot.idea.id = :ideaId
              and lot.status = :status
            """)
    long sumActivePrincipal(
            @Param("userId") UUID userId,
            @Param("ideaId") UUID ideaId,
            @Param("status") SeedUnitLot.Status status);
}
