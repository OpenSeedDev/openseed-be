package com.seedrank.auth.login;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s join fetch s.user where s.refreshTokenHash=:hash")
    Optional<AuthSession> findByHashForUpdate(@Param("hash") String hash);
    @Modifying
    @Query("update AuthSession s set s.revokedAt=:now, s.revocationReason=:reason where s.familyId=:family and s.revokedAt is null")
    int revokeFamily(@Param("family") UUID family, @Param("now") Instant now, @Param("reason") String reason);
}
