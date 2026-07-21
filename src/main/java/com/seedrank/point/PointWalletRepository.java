package com.seedrank.point;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointWalletRepository extends JpaRepository<PointWallet, UUID> {
    Optional<PointWallet> findByUserId(UUID userId);
}
