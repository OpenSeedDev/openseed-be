package com.seedrank.unit;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.member.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "seed_unit_lots")
public class SeedUnitLot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int units;

    @Column(name = "purchase_price", nullable = false)
    private int purchasePrice;

    @Column(nullable = false)
    private int principal;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    protected SeedUnitLot() {
    }

    private SeedUnitLot(UUID id, Idea idea, User user, int units, int purchasePrice, Instant purchasedAt) {
        this.id = id;
        this.idea = idea;
        this.user = user;
        this.units = units;
        this.purchasePrice = purchasePrice;
        this.principal = Math.multiplyExact(units, purchasePrice);
        this.purchasedAt = purchasedAt;
        this.unlockedAt = purchasedAt.plusSeconds(24 * 60 * 60);
        this.status = Status.LOCKED;
    }

    public static SeedUnitLot locked(Idea idea, User user, int units, int purchasePrice, Instant purchasedAt) {
        return new SeedUnitLot(UUID.randomUUID(), idea, user, units, purchasePrice, purchasedAt);
    }

    public UUID id() { return id; }
    public UUID ideaId() { return idea.id(); }
    public UUID userId() { return user.getId(); }
    public int units() { return units; }
    public int purchasePrice() { return purchasePrice; }
    public int principal() { return principal; }
    public Instant purchasedAt() { return purchasedAt; }
    public Instant unlockedAt() { return unlockedAt; }
    public Status status() { return status; }

    public enum Status {
        LOCKED,
        RECOVERED
    }
}
