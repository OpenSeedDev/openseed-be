package com.seedrank.unit.recovery.payout;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.seedrank.member.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pending_recovery_payouts")
class PendingRecoveryPayout {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "paid_amount", nullable = false) private int paidAmount;
    @Column(name = "balance_after", nullable = false) private int balanceAfter;
    @Column(name = "policy_date", nullable = false) private LocalDate policyDate;
    @Column(name = "paid_at", nullable = false) private Instant paidAt;

    protected PendingRecoveryPayout() {}

    static PendingRecoveryPayout create(
            User user, int paidAmount, int balanceAfter, LocalDate policyDate, Instant paidAt) {
        if (paidAmount <= 0) throw new IllegalArgumentException("Payout amount must be positive");
        PendingRecoveryPayout payout = new PendingRecoveryPayout();
        payout.id = UUID.randomUUID();
        payout.user = user;
        payout.paidAmount = paidAmount;
        payout.balanceAfter = balanceAfter;
        payout.policyDate = policyDate;
        payout.paidAt = paidAt;
        return payout;
    }

    UUID id() { return id; }
    int paidAmount() { return paidAmount; }
    int balanceAfter() { return balanceAfter; }
    LocalDate policyDate() { return policyDate; }
    Instant paidAt() { return paidAt; }
}
