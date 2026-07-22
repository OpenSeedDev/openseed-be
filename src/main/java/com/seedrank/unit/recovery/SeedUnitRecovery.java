package com.seedrank.unit.recovery;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.member.User;
import com.seedrank.unit.SeedUnitLot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "seed_unit_recoveries")
class SeedUnitRecovery {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, unique = true)
    private SeedUnitLot lot;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;
    @Column(nullable = false) private int units;
    @Column(name = "recovery_price", nullable = false) private int recoveryPrice;
    @Column(name = "realized_amount", nullable = false) private int realizedAmount;
    @Column(name = "wallet_paid_amount", nullable = false) private int walletPaidAmount;
    @Column(name = "pending_amount", nullable = false) private int pendingAmount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected SeedUnitRecovery() {}

    static SeedUnitRecovery create(SeedUnitLot lot, int recoveryPrice, int walletPaidAmount, Instant now) {
        SeedUnitRecovery recovery = new SeedUnitRecovery();
        recovery.id = UUID.randomUUID();
        recovery.lot = lot;
        recovery.user = lot.user();
        recovery.idea = lot.idea();
        recovery.units = lot.units();
        recovery.recoveryPrice = recoveryPrice;
        recovery.realizedAmount = Math.multiplyExact(lot.units(), recoveryPrice);
        recovery.walletPaidAmount = walletPaidAmount;
        recovery.pendingAmount = recovery.realizedAmount - walletPaidAmount;
        recovery.createdAt = now;
        return recovery;
    }

    UUID id() { return id; }
    UUID lotId() { return lot.id(); }
    UUID ideaId() { return idea.id(); }
    int units() { return units; }
    int recoveryPrice() { return recoveryPrice; }
    int realizedAmount() { return realizedAmount; }
    int walletPaidAmount() { return walletPaidAmount; }
    int pendingAmount() { return pendingAmount; }
    Instant createdAt() { return createdAt; }
}
