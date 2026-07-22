package com.seedrank.unit.recovery;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.point.PointLedger;
import com.seedrank.point.PointLedgerRepository;
import com.seedrank.point.PointWallet;
import com.seedrank.point.PointWalletRepository;
import com.seedrank.unit.SeedUnitLot;
import com.seedrank.unit.SeedUnitLotRepository;

@Service
class SeedUnitRecoveryService {
    static final int DAILY_RECOVERY_PAYMENT_CAP = 500;
    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");
    private static final String NON_MONETARY_NOTICE =
            "Seed Unit은 현금, 지분, 배당 또는 수익권이 아니며 현금화할 수 없습니다.";

    private final AccessTokenAuthenticator authenticator;
    private final SeedUnitLotRepository lots;
    private final SeedUnitRecoveryRepository recoveries;
    private final PointWalletRepository wallets;
    private final PointLedgerRepository ledgers;
    private final Clock clock;

    SeedUnitRecoveryService(AccessTokenAuthenticator authenticator, SeedUnitLotRepository lots,
            SeedUnitRecoveryRepository recoveries, PointWalletRepository wallets,
            PointLedgerRepository ledgers, Clock clock) {
        this.authenticator = authenticator;
        this.lots = lots;
        this.recoveries = recoveries;
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.clock = clock;
    }

    @Transactional
    SeedUnitRecoveryResponse recover(String authorization, UUID lotId) {
        UUID userId = authenticator.authenticate(authorization).userId();
        SeedUnitLot lot = lots.findByIdForUpdate(lotId).orElseThrow(SeedUnitLotNotFoundException::new);
        if (!lot.userId().equals(userId)) throw new SeedUnitLotNotFoundException();
        var existing = recoveries.findByLotId(lotId);
        PointWallet wallet = wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Point wallet not found"));
        if (existing.isPresent()) {
            return SeedUnitRecoveryResponse.from(
                    existing.get(), wallet.getBalance(), wallet.getPendingRecoveryBalance(), NON_MONETARY_NOTICE);
        }

        Instant now = clock.instant();
        if (now.isBefore(lot.unlockedAt())) throw new SeedUnitLockedException();
        int realizedAmount = Math.multiplyExact(lot.units(), lot.currentUnitPrice());
        LocalDate policyDate = now.atZone(POLICY_ZONE).toLocalDate();
        Instant dayStart = policyDate.atStartOfDay(POLICY_ZONE).toInstant();
        Instant nextDayStart = policyDate.plusDays(1).atStartOfDay(POLICY_ZONE).toInstant();
        int dailyCapacity = Math.max(0, DAILY_RECOVERY_PAYMENT_CAP - Math.toIntExact(
                recoveries.sumWalletPaidAmount(userId, dayStart, nextDayStart)));
        int requestedPayment = Math.min(realizedAmount, dailyCapacity);
        int paidAmount = wallet.creditRecovery(requestedPayment, now);
        int pendingAmount = realizedAmount - paidAmount;
        wallet.addPendingRecovery(pendingAmount, now);
        lot.recover(now);
        SeedUnitRecovery recovery = recoveries.save(
                SeedUnitRecovery.create(lot, lot.currentUnitPrice(), paidAmount, now));
        if (paidAmount > 0) {
            ledgers.save(PointLedger.unitRecovery(
                    lot.user(), lot.id(), paidAmount, wallet.getBalance(), policyDate, now));
        }
        return SeedUnitRecoveryResponse.from(
                recovery, wallet.getBalance(), wallet.getPendingRecoveryBalance(), NON_MONETARY_NOTICE);
    }
}
