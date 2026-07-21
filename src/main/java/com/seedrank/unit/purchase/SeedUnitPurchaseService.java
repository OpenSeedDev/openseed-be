package com.seedrank.unit.purchase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.member.UserRepository;
import com.seedrank.point.PointLedger;
import com.seedrank.point.PointLedgerRepository;
import com.seedrank.point.PointWallet;
import com.seedrank.point.PointWalletRepository;
import com.seedrank.unit.SeedUnitLot;
import com.seedrank.unit.SeedUnitLotRepository;

@Service
class SeedUnitPurchaseService {
    static final int SINGLE_PURCHASE_CAP = 100;
    static final int DAILY_PURCHASE_CAP = 300;
    static final int IDEA_ACTIVE_PRINCIPAL_CAP = 300;
    private static final int LOCK_HOURS = 24;
    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");
    private static final String NON_MONETARY_NOTICE =
            "Seed Unit은 현금, 지분, 배당 또는 수익권이 아니며 현금화할 수 없습니다.";

    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final UserRepository users;
    private final PointWalletRepository wallets;
    private final PointLedgerRepository ledgers;
    private final SeedUnitLotRepository lots;
    private final Clock clock;

    SeedUnitPurchaseService(AccessTokenAuthenticator authenticator, IdeaRepository ideas, UserRepository users,
            PointWalletRepository wallets, PointLedgerRepository ledgers, SeedUnitLotRepository lots, Clock clock) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.users = users;
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.lots = lots;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    SeedUnitPreviewResponse preview(String authorization, UUID ideaId, SeedUnitPreviewRequest request) {
        UUID userId = authenticator.authenticate(authorization).userId();
        Idea idea = publishedIdea(ideaId);
        rejectSelfPurchase(idea, userId);
        PointWallet wallet = wallets.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Point wallet not found"));
        int total = total(request.units(), idea.currentUnitPrice());
        validateSingleLimit(total);
        Instant now = clock.instant();
        return new SeedUnitPreviewResponse(idea.id(), request.units(), idea.currentUnitPrice(), total,
                wallet.getBalance() - total, LOCK_HOURS, now.plusSeconds(LOCK_HOURS * 60L * 60L), NON_MONETARY_NOTICE);
    }

    @Transactional
    SeedUnitPurchaseResponse purchase(String authorization, UUID ideaId, SeedUnitPurchaseRequest request) {
        UUID userId = authenticator.authenticate(authorization).userId();
        Idea idea = publishedIdea(ideaId);
        rejectSelfPurchase(idea, userId);
        if (request.confirmedUnitPrice() != idea.currentUnitPrice()) {
            throw new UnitPriceChangedException();
        }

        int principal = total(request.units(), idea.currentUnitPrice());
        validateSingleLimit(principal);
        Instant now = clock.instant();
        LocalDate policyDate = now.atZone(POLICY_ZONE).toLocalDate();
        if (ledgers.sumUnitPurchasePrincipal(userId, policyDate, PointLedger.SourceType.UNIT_PURCHASE)
                        + principal > DAILY_PURCHASE_CAP
                || lots.sumActivePrincipal(userId, ideaId, SeedUnitLot.Status.LOCKED)
                        + principal > IDEA_ACTIVE_PRINCIPAL_CAP) {
            throw new PurchaseLimitExceededException();
        }

        PointWallet wallet = wallets.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Point wallet not found"));
        if (wallet.getBalance() < principal) {
            throw new InsufficientPointException();
        }

        var user = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found"));
        SeedUnitLot lot = lots.save(SeedUnitLot.locked(idea, user, request.units(), idea.currentUnitPrice(), now));
        wallet.debit(principal, now);
        ledgers.save(PointLedger.unitPurchase(user, lot.id(), principal, wallet.getBalance(), policyDate, now));
        return SeedUnitPurchaseResponse.from(lot, wallet.getBalance(), NON_MONETARY_NOTICE);
    }

    private Idea publishedIdea(UUID ideaId) {
        Idea idea = ideas.findById(ideaId).orElseThrow(UnitPurchaseIdeaNotFoundException::new);
        if (idea.status() != IdeaStatus.PUBLISHED) {
            throw new UnitPurchaseIdeaNotFoundException();
        }
        return idea;
    }

    private void rejectSelfPurchase(Idea idea, UUID userId) {
        if (idea.authorId().equals(userId)) {
            throw new SelfUnitPurchaseException();
        }
    }

    private int total(int units, int unitPrice) {
        try {
            return Math.multiplyExact(units, unitPrice);
        } catch (ArithmeticException exception) {
            throw new PurchaseLimitExceededException();
        }
    }

    private void validateSingleLimit(int principal) {
        if (principal > SINGLE_PURCHASE_CAP) {
            throw new PurchaseLimitExceededException();
        }
    }
}
