package com.seedrank.auth.login;

import java.time.Instant;
import java.util.UUID;
import com.seedrank.member.User;
import jakarta.persistence.*;

@Entity
@Table(name = "auth_sessions")
class AuthSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 43) private String refreshTokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected AuthSession() {}
    AuthSession(User user, String hash, Instant now) {
        this.id = UUID.randomUUID(); this.user = user; this.refreshTokenHash = hash;
        this.createdAt = now; this.expiresAt = now.plusSeconds(14 * 24 * 60 * 60L);
    }
}
