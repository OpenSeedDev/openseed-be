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
    public static final int MAX_BALANCE = 2_000;

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

    int credit(int requestedAmount, Instant now) {
        int paidAmount = Math.min(requestedAmount, MAX_BALANCE - balance);
        balance += paidAmount;
        if (paidAmount > 0) {
            updatedAt = now;
        }
        return paidAmount;
    }

    public void debit(int amount, Instant now) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("Point debit exceeds available balance");
        }
        balance -= amount;
        updatedAt = now;
    }

    public int creditRecovery(int requestedAmount, Instant now) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("Recovery amount must not be negative");
        }
        int paidAmount = Math.min(requestedAmount, MAX_BALANCE - balance);
        balance += paidAmount;
        if (paidAmount > 0) updatedAt = now;
        return paidAmount;
    }

    public void addPendingRecovery(int amount, Instant now) {
        if (amount < 0) {
            throw new IllegalArgumentException("Pending recovery amount must not be negative");
        }
        pendingRecoveryBalance = Math.addExact(pendingRecoveryBalance, amount);
        if (amount > 0) updatedAt = now;
    }

    User getUser() {
        return user;
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
