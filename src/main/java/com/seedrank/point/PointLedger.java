package com.seedrank.point;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "point_ledgers")
public class PointLedger {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(name = "original_amount", nullable = false)
    private int originalAmount;

    @Column(name = "paid_amount", nullable = false)
    private int paidAmount;

    @Column(name = "expired_amount", nullable = false)
    private int expiredAmount;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PointLedger() {
    }

    private PointLedger(UUID id, User user, Instant now) {
        this.id = id;
        this.user = user;
        this.type = Type.CREDIT;
        this.originalAmount = PointWallet.SIGNUP_BALANCE;
        this.paidAmount = PointWallet.SIGNUP_BALANCE;
        this.expiredAmount = 0;
        this.balanceAfter = PointWallet.SIGNUP_BALANCE;
        this.sourceType = SourceType.SIGNUP_BONUS;
        this.sourceId = user.getId();
        this.createdAt = now;
    }

    public static PointLedger signupBonus(User user, Instant now) {
        return new PointLedger(UUID.randomUUID(), user, now);
    }

    public enum Type {
        CREDIT
    }

    public enum SourceType {
        SIGNUP_BONUS
    }
}
