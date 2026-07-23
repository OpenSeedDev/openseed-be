package com.seedrank.unit.consistency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.seedrank.TestcontainersConfiguration;

import javax.sql.DataSource;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.auth.jwt-secret=test-signing-key-with-at-least-32-bytes",
        "app.financial-consistency.enabled=false"
})
class FinancialConsistencyCheckIntegrationTest {
    private static final Instant BASE = Instant.parse("2026-07-22T08:00:00Z");

    @Autowired FinancialConsistencyCheckService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE financial_consistency_findings, financial_consistency_checks");
        jdbc.update("DELETE FROM pending_recovery_payouts");
        jdbc.update("DELETE FROM seed_unit_recoveries");
        jdbc.update("DELETE FROM seed_unit_lots");
        jdbc.update("DELETE FROM idea_timeline_events");
        jdbc.execute("TRUNCATE TABLE idea_versions");
        jdbc.update("DELETE FROM validation_questions");
        jdbc.update("DELETE FROM ideas");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.execute("TRUNCATE TABLE point_ledgers");
        jdbc.update("DELETE FROM point_wallets");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void recordsAConsistentPurchaseRecoveryAndPendingPayoutJourney() {
        Journey journey = consistentJourney();

        FinancialConsistencyCheckResult result = service.run().orElseThrow();

        assertThat(result.status()).isEqualTo(FinancialConsistencyStatus.CONSISTENT);
        assertThat(result.findingCount()).isZero();
        assertThat(findingCodes(result.checkId())).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM financial_consistency_checks WHERE id=?", Integer.class, result.checkId()))
                .isEqualTo(1);
        assertThat(journey.userId()).isNotNull();
    }

    @Test
    void detectsWalletLedgerChainAndPendingBalanceMismatches() {
        Journey journey = consistentJourney();
        jdbc.update("UPDATE point_wallets SET balance=631, pending_recovery_balance=301 WHERE user_id=?",
                journey.userId());
        insertLedger(journey.userId(), "CREDIT", 5, 999, "SIGNUP_BONUS", UUID.randomUUID(), BASE.plusSeconds(50));

        FinancialConsistencyCheckResult result = service.run().orElseThrow();

        assertThat(result.status()).isEqualTo(FinancialConsistencyStatus.INCONSISTENT);
        assertThat(findingCodes(result.checkId())).contains(
                "WALLET_LATEST_LEDGER_BALANCE",
                "LEDGER_BALANCE_CHAIN",
                "WALLET_PENDING_RECOVERY_BALANCE");
    }

    @Test
    void detectsMissingLotRecoveryAndSourceLedgers() {
        Journey journey = consistentJourney();
        UUID missingPurchaseLedgerLot = insertLot(
                journey.ideaId(), journey.userId(), 1, 10, "LOCKED", BASE.plusSeconds(60));
        UUID recoveredWithoutRecoveryLot = insertLot(
                journey.ideaId(), journey.userId(), 1, 10, "RECOVERED", BASE.plusSeconds(65));
        UUID missingRecoveryLedgerLot = insertLot(
                journey.ideaId(), journey.userId(), 2, 10, "RECOVERED", BASE.plusSeconds(70));
        insertRecovery(missingRecoveryLedgerLot, journey, 2, 10, 20, 20, 0, BASE.plusSeconds(80));
        UUID lockedWithRecoveryLot = insertLot(
                journey.ideaId(), journey.userId(), 1, 10, "LOCKED", BASE.plusSeconds(90));
        insertRecovery(lockedWithRecoveryLot, journey, 1, 10, 10, 0, 10, BASE.plusSeconds(100));
        UUID payoutId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pending_recovery_payouts(id, user_id, paid_amount, balance_after, policy_date, paid_at)
                VALUES (?, ?, 10, 640, '2026-07-22', ?)
                """, payoutId, journey.userId(), timestamp(BASE.plusSeconds(110)));

        FinancialConsistencyCheckResult result = service.run().orElseThrow();

        assertThat(findingCodes(result.checkId())).contains(
                "LOT_PURCHASE_LEDGER",
                "RECOVERED_LOT_RECOVERY",
                "LOCKED_LOT_RECOVERY",
                "RECOVERY_LEDGER",
                "PENDING_PAYOUT_LEDGER");
        assertThat(findingEntityIds(result.checkId())).contains(
                missingPurchaseLedgerLot, recoveredWithoutRecoveryLot,
                missingRecoveryLedgerLot, lockedWithRecoveryLot, payoutId);
    }

    @Test
    void keepsPreviousFindingsAppendOnlyAfterANewCleanRun() {
        Journey journey = consistentJourney();
        jdbc.update("UPDATE point_wallets SET balance=631 WHERE user_id=?", journey.userId());
        FinancialConsistencyCheckResult inconsistent = service.run().orElseThrow();
        jdbc.update("UPDATE point_wallets SET balance=630 WHERE user_id=?", journey.userId());

        FinancialConsistencyCheckResult consistent = service.run().orElseThrow();

        assertThat(consistent.status()).isEqualTo(FinancialConsistencyStatus.CONSISTENT);
        assertThat(findingCodes(inconsistent.checkId())).contains("WALLET_LATEST_LEDGER_BALANCE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM financial_consistency_checks", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE financial_consistency_checks SET finding_count=0 WHERE id=?", inconsistent.checkId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void skipsAnOverlappingRunWithoutCreatingAnotherCheck() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            connection.setAutoCommit(false);
            statement.setLong(1, 8_102_037L);
            statement.execute();

            assertThat(service.run()).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM financial_consistency_checks", Integer.class)).isZero();
            connection.rollback();
        }
    }

    private Journey consistentJourney() {
        UUID userId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        insertUser(userId, "holder@example.com", "holder_1");
        insertUser(authorId, "author@example.com", "author_1");
        UUID ideaId = insertIdea(authorId);
        UUID lotId = insertLot(ideaId, userId, 7, 10, "RECOVERED", BASE.plusSeconds(10));
        insertLedger(userId, "DEBIT", 70, 230, "UNIT_PURCHASE", lotId, BASE.plusSeconds(11));
        UUID recoveryId = insertRecovery(lotId, new Journey(userId, ideaId),
                7, 100, 700, 300, 400, BASE.plusSeconds(20));
        insertLedger(userId, "CREDIT", 300, 530, "UNIT_RECOVERY", lotId, BASE.plusSeconds(21));
        UUID payoutId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pending_recovery_payouts(id, user_id, paid_amount, balance_after, policy_date, paid_at)
                VALUES (?, ?, 100, 630, '2026-07-22', ?)
                """, payoutId, userId, timestamp(BASE.plusSeconds(30)));
        insertLedger(userId, "CREDIT", 100, 630, "PENDING_RECOVERY_PAYOUT", payoutId, BASE.plusSeconds(31));
        jdbc.update("UPDATE point_wallets SET balance=630, pending_recovery_balance=300 WHERE user_id=?", userId);
        assertThat(recoveryId).isNotNull();
        return new Journey(userId, ideaId);
    }

    private void insertUser(UUID userId, String email, String profileId) {
        jdbc.update("""
                INSERT INTO users(id, email, password_hash, profile_id, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'USER', 'ACTIVE', ?, ?)
                """, userId, email, profileId, timestamp(BASE), timestamp(BASE));
        jdbc.update("""
                INSERT INTO point_wallets(id, user_id, balance, pending_recovery_balance, updated_at)
                VALUES (?, ?, 300, 0, ?)
                """, UUID.randomUUID(), userId, timestamp(BASE));
        insertLedger(userId, "CREDIT", 300, 300, "SIGNUP_BONUS", userId, BASE);
    }

    private UUID insertIdea(UUID authorId) {
        UUID ideaId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ideas(id, author_id, status, title, category, summary, problem, target_customer,
                                  solution, business_model, visibility, current_unit_price, published_at,
                                  created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', '정합성 대상', 'SERVICE', '요약', '문제', '고객', '해결', '모델',
                        'PUBLIC', 100, ?, ?, ?)
                """, ideaId, authorId, timestamp(BASE), timestamp(BASE), timestamp(BASE));
        return ideaId;
    }

    private UUID insertLot(UUID ideaId, UUID userId, int units, int price, String status, Instant at) {
        UUID lotId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO seed_unit_lots(id, idea_id, user_id, units, purchase_price, principal,
                                           purchase_request_key, purchased_at, unlocked_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, lotId, ideaId, userId, units, price, units * price, "consistency-" + lotId,
                timestamp(at), timestamp(at.plusSeconds(86_400)), status);
        return lotId;
    }

    private UUID insertRecovery(UUID lotId, Journey journey, int units, int price, int realized,
            int paid, int pending, Instant at) {
        UUID recoveryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO seed_unit_recoveries(id, lot_id, user_id, idea_id, units, recovery_price,
                                                 realized_amount, wallet_paid_amount, pending_amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, recoveryId, lotId, journey.userId(), journey.ideaId(), units, price, realized, paid, pending,
                timestamp(at));
        return recoveryId;
    }

    private void insertLedger(UUID userId, String type, int paid, int balanceAfter,
            String sourceType, UUID sourceId, Instant at) {
        jdbc.update("""
                INSERT INTO point_ledgers(id, user_id, type, original_amount, paid_amount, expired_amount,
                                          balance_after, source_type, source_id, policy_date, created_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, type, paid, paid, balanceAfter, sourceType, sourceId,
                sourceType.equals("SIGNUP_BONUS") ? null : java.sql.Date.valueOf("2026-07-22"), timestamp(at));
    }

    private List<String> findingCodes(UUID checkId) {
        return jdbc.queryForList(
                "SELECT code FROM financial_consistency_findings WHERE check_id=? ORDER BY code", String.class,
                checkId);
    }

    private List<UUID> findingEntityIds(UUID checkId) {
        return jdbc.queryForList(
                "SELECT entity_id FROM financial_consistency_findings WHERE check_id=? AND entity_id IS NOT NULL",
                UUID.class, checkId);
    }

    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    private record Journey(UUID userId, UUID ideaId) {}
}
