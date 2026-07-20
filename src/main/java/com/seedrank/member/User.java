package com.seedrank.member;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "profile_id", nullable = false, length = 20)
    private String profileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    private User(UUID id, String email, String passwordHash, String profileId, Instant now) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.profileId = profileId;
        this.role = Role.USER;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User create(String email, String passwordHash, String profileId, Instant now) {
        return new User(UUID.randomUUID(), email, passwordHash, profileId, now);
    }

    public UUID getId() {
        return id;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum Role {
        USER
    }

    public enum Status {
        ACTIVE, SUSPENDED
    }
}
