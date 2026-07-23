package com.seedrank.point;

import java.time.Instant;
import java.time.LocalDate;
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

    @Column(name = "reward_scope_id")
    private UUID rewardScopeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "policy_date")
    private LocalDate policyDate;

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

    static PointLedger activityReward(
            User user,
            SourceType sourceType,
            UUID sourceId,
            int originalAmount,
            int paidAmount,
            int balanceAfter,
            LocalDate policyDate,
            Instant now) {
        return activityReward(user, sourceType, sourceId, null, originalAmount, paidAmount, balanceAfter, policyDate, now);
    }

    static PointLedger activityReward(
            User user,
            SourceType sourceType,
            UUID sourceId,
            UUID rewardScopeId,
            int originalAmount,
            int paidAmount,
            int balanceAfter,
            LocalDate policyDate,
            Instant now) {
        PointLedger ledger = new PointLedger();
        ledger.id = UUID.randomUUID();
        ledger.user = user;
        ledger.type = Type.CREDIT;
        ledger.originalAmount = originalAmount;
        ledger.paidAmount = paidAmount;
        ledger.expiredAmount = originalAmount - paidAmount;
        ledger.balanceAfter = balanceAfter;
        ledger.sourceType = sourceType;
        ledger.sourceId = sourceId;
        ledger.rewardScopeId = rewardScopeId;
        ledger.createdAt = now;
        ledger.policyDate = policyDate;
        return ledger;
    }

    public static PointLedger unitPurchase(
            User user,
            UUID lotId,
            int principal,
            int balanceAfter,
            LocalDate policyDate,
            Instant now) {
        PointLedger ledger = new PointLedger();
        ledger.id = UUID.randomUUID();
        ledger.user = user;
        ledger.type = Type.DEBIT;
        ledger.originalAmount = principal;
        ledger.paidAmount = principal;
        ledger.expiredAmount = 0;
        ledger.balanceAfter = balanceAfter;
        ledger.sourceType = SourceType.UNIT_PURCHASE;
        ledger.sourceId = lotId;
        ledger.createdAt = now;
        ledger.policyDate = policyDate;
        return ledger;
    }

    public static PointLedger unitRecovery(
            User user, UUID lotId, int paidAmount, int balanceAfter, LocalDate policyDate, Instant now) {
        PointLedger ledger = new PointLedger();
        ledger.id = UUID.randomUUID();
        ledger.user = user;
        ledger.type = Type.CREDIT;
        ledger.originalAmount = paidAmount;
        ledger.paidAmount = paidAmount;
        ledger.expiredAmount = 0;
        ledger.balanceAfter = balanceAfter;
        ledger.sourceType = SourceType.UNIT_RECOVERY;
        ledger.sourceId = lotId;
        ledger.createdAt = now;
        ledger.policyDate = policyDate;
        return ledger;
    }

    public static PointLedger pendingRecoveryPayout(
            User user, UUID payoutId, int paidAmount, int balanceAfter, LocalDate policyDate, Instant now) {
        PointLedger ledger = new PointLedger();
        ledger.id = UUID.randomUUID();
        ledger.user = user;
        ledger.type = Type.CREDIT;
        ledger.originalAmount = paidAmount;
        ledger.paidAmount = paidAmount;
        ledger.expiredAmount = 0;
        ledger.balanceAfter = balanceAfter;
        ledger.sourceType = SourceType.PENDING_RECOVERY_PAYOUT;
        ledger.sourceId = payoutId;
        ledger.createdAt = now;
        ledger.policyDate = policyDate;
        return ledger;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public int getOriginalAmount() {
        return originalAmount;
    }

    public int getPaidAmount() {
        return paidAmount;
    }

    public int getExpiredAmount() {
        return expiredAmount;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getRewardScopeId() { return rewardScopeId; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getPolicyDate() {
        return policyDate;
    }

    public enum Type {
        CREDIT,
        DEBIT
    }

    public enum SourceType {
        SIGNUP_BONUS,
        DAILY_FIRST_ACCESS,
        IDEA_PUBLISHED,
        FEEDBACK_CREATED,
        FEEDBACK_ACCEPTED,
        UNIT_PURCHASE,
        UNIT_RECOVERY,
        PENDING_RECOVERY_PAYOUT
    }
}
