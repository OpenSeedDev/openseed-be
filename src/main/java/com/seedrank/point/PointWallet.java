package com.seedrank.point;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.member.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_wallets")
public class PointWallet {

    public static final int SIGNUP_BALANCE = 300;

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private int balance;

    @Column(name = "pending_recovery_balance", nullable = false)
    private int pendingRecoveryBalance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PointWallet() {
    }

    private PointWallet(UUID id, User user, Instant now) {
        this.id = id;
        this.user = user;
        this.balance = SIGNUP_BALANCE;
        this.pendingRecoveryBalance = 0;
        this.updatedAt = now;
    }

    public static PointWallet signupWallet(User user, Instant now) {
        return new PointWallet(UUID.randomUUID(), user, now);
    }

    public int getBalance() {
        return balance;
    }

    public int getPendingRecoveryBalance() {
        return pendingRecoveryBalance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
