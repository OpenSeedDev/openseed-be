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
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "rotated_from_id", unique = true) private UUID rotatedFromId;
    @Column(name = "revocation_reason", length = 30) private String revocationReason;
    protected AuthSession() {}
    AuthSession(User user, String hash, Instant now) {
        this.id = UUID.randomUUID(); this.user = user; this.refreshTokenHash = hash; this.familyId = this.id;
        this.createdAt = now; this.expiresAt = now.plusSeconds(14 * 24 * 60 * 60L);
    }
    private AuthSession(User user, String hash, Instant now, UUID familyId, UUID rotatedFromId) {
        this.id=UUID.randomUUID(); this.user=user; this.refreshTokenHash=hash; this.createdAt=now;
        this.expiresAt=now.plusSeconds(14*24*60*60L); this.familyId=familyId; this.rotatedFromId=rotatedFromId;
    }
    AuthSession rotateTo(String hash, Instant now) { revoke(now, "ROTATED"); return new AuthSession(user, hash, now, familyId, id); }
    void revoke(Instant now, String reason) { if (revokedAt==null) { revokedAt=now; revocationReason=reason; } }
    UUID id() { return id; }
    UUID familyId() { return familyId; }
    User user() { return user; }
    Instant expiresAt() { return expiresAt; }
    boolean revoked() { return revokedAt != null; }
}
