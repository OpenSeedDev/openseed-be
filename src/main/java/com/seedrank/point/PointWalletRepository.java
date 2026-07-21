package com.seedrank.point;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PointWalletRepository extends JpaRepository<PointWallet, UUID> {
    Optional<PointWallet> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from PointWallet wallet join fetch wallet.user where wallet.user.id=:userId")
    Optional<PointWallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}
