package com.seedrank.unit.consistency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FinancialConsistencyCheckService {
    private static final long ADVISORY_LOCK_KEY = 8_102_037L;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    FinancialConsistencyCheckService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    Optional<FinancialConsistencyCheckResult> run() {
        Boolean acquired = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(acquired)) return Optional.empty();

        Instant startedAt = clock.instant();
        List<FinancialConsistencyFinding> findings = new ArrayList<>();
        findings.addAll(walletLatestLedgerFindings());
        findings.addAll(ledgerChainFindings());
        findings.addAll(pendingBalanceFindings());
        findings.addAll(lotPurchaseLedgerFindings());
        findings.addAll(lotRecoveryStateFindings());
        findings.addAll(recoveryLedgerFindings());
        findings.addAll(payoutLedgerFindings());

        UUID checkId = UUID.randomUUID();
        FinancialConsistencyStatus status = findings.isEmpty()
                ? FinancialConsistencyStatus.CONSISTENT
                : FinancialConsistencyStatus.INCONSISTENT;
        Instant completedAt = clock.instant();
        jdbc.update("""
                INSERT INTO financial_consistency_checks(
                    id, status, finding_count, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?)
                """, checkId, status.name(), findings.size(), Timestamp.from(startedAt), Timestamp.from(completedAt));
        for (FinancialConsistencyFinding finding : findings) {
            jdbc.update("""
                    INSERT INTO financial_consistency_findings(
                        id, check_id, code, user_id, entity_type, entity_id,
                        expected_value, actual_value, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), checkId, finding.code(), finding.userId(), finding.entityType(),
                    finding.entityId(), finding.expectedValue(), finding.actualValue(), Timestamp.from(completedAt));
        }
        return Optional.of(new FinancialConsistencyCheckResult(checkId, status, findings.size()));
    }

    private List<FinancialConsistencyFinding> walletLatestLedgerFindings() {
        return query("""
                SELECT 'WALLET_LATEST_LEDGER_BALANCE' AS code, w.user_id, 'WALLET' AS entity_type,
                       w.id AS entity_id, latest.balance_after::bigint AS expected_value,
                       w.balance::bigint AS actual_value
                FROM point_wallets w
                LEFT JOIN LATERAL (
                    SELECT l.balance_after
                    FROM point_ledgers l
                    WHERE l.user_id = w.user_id
                    ORDER BY l.created_at DESC, l.id DESC
                    LIMIT 1
                ) latest ON TRUE
                WHERE latest.balance_after IS NULL OR latest.balance_after <> w.balance
                """);
    }

    private List<FinancialConsistencyFinding> ledgerChainFindings() {
        return query("""
                WITH ordered AS (
                    SELECT l.id, l.user_id, l.type, l.paid_amount, l.balance_after,
                           lag(l.balance_after, 1, 0) OVER (
                               PARTITION BY l.user_id ORDER BY l.created_at, l.id) AS previous_balance
                    FROM point_ledgers l
                )
                SELECT 'LEDGER_BALANCE_CHAIN' AS code, user_id, 'LEDGER' AS entity_type,
                       id AS entity_id,
                       (previous_balance + CASE WHEN type='CREDIT' THEN paid_amount ELSE -paid_amount END)::bigint
                           AS expected_value,
                       balance_after::bigint AS actual_value
                FROM ordered
                WHERE balance_after <>
                    previous_balance + CASE WHEN type='CREDIT' THEN paid_amount ELSE -paid_amount END
                """);
    }

    private List<FinancialConsistencyFinding> pendingBalanceFindings() {
        return query("""
                SELECT 'WALLET_PENDING_RECOVERY_BALANCE' AS code, w.user_id,
                       'WALLET' AS entity_type, w.id AS entity_id,
                       (coalesce(r.pending, 0) - coalesce(p.paid, 0))::bigint AS expected_value,
                       w.pending_recovery_balance::bigint AS actual_value
                FROM point_wallets w
                LEFT JOIN (
                    SELECT user_id, sum(pending_amount) AS pending
                    FROM seed_unit_recoveries GROUP BY user_id
                ) r ON r.user_id=w.user_id
                LEFT JOIN (
                    SELECT user_id, sum(paid_amount) AS paid
                    FROM pending_recovery_payouts GROUP BY user_id
                ) p ON p.user_id=w.user_id
                WHERE w.pending_recovery_balance <> coalesce(r.pending, 0) - coalesce(p.paid, 0)
                """);
    }

    private List<FinancialConsistencyFinding> lotPurchaseLedgerFindings() {
        return query("""
                SELECT 'LOT_PURCHASE_LEDGER' AS code, lot.user_id, 'LOT' AS entity_type,
                       lot.id AS entity_id, lot.principal::bigint AS expected_value,
                       ledger.paid_amount::bigint AS actual_value
                FROM seed_unit_lots lot
                LEFT JOIN point_ledgers ledger
                  ON ledger.source_type='UNIT_PURCHASE' AND ledger.source_id=lot.id
                WHERE ledger.id IS NULL OR ledger.user_id<>lot.user_id OR ledger.type<>'DEBIT'
                   OR ledger.paid_amount<>lot.principal
                """);
    }

    private List<FinancialConsistencyFinding> lotRecoveryStateFindings() {
        List<FinancialConsistencyFinding> findings = query("""
                SELECT CASE WHEN lot.status='RECOVERED'
                            THEN 'RECOVERED_LOT_RECOVERY'
                            ELSE 'LOCKED_LOT_RECOVERY' END AS code,
                       lot.user_id, 'LOT' AS entity_type, lot.id AS entity_id,
                       CASE WHEN lot.status='RECOVERED' THEN 1::bigint ELSE 0::bigint END AS expected_value,
                       CASE WHEN recovery.id IS NULL THEN 0::bigint ELSE 1::bigint END AS actual_value
                FROM seed_unit_lots lot
                LEFT JOIN seed_unit_recoveries recovery ON recovery.lot_id=lot.id
                WHERE (lot.status='RECOVERED' AND recovery.id IS NULL)
                   OR (lot.status='LOCKED' AND recovery.id IS NOT NULL)
                """);
        findings.addAll(query("""
                SELECT 'RECOVERY_LOT_DETAILS' AS code, lot.user_id, 'RECOVERY' AS entity_type,
                       recovery.id AS entity_id, lot.units::bigint AS expected_value,
                       recovery.units::bigint AS actual_value
                FROM seed_unit_recoveries recovery
                JOIN seed_unit_lots lot ON lot.id=recovery.lot_id
                WHERE recovery.user_id<>lot.user_id OR recovery.idea_id<>lot.idea_id
                   OR recovery.units<>lot.units
                """));
        return findings;
    }

    private List<FinancialConsistencyFinding> recoveryLedgerFindings() {
        return query("""
                SELECT 'RECOVERY_LEDGER' AS code, recovery.user_id, 'LOT' AS entity_type,
                       recovery.lot_id AS entity_id, recovery.wallet_paid_amount::bigint AS expected_value,
                       ledger.paid_amount::bigint AS actual_value
                FROM seed_unit_recoveries recovery
                LEFT JOIN point_ledgers ledger
                  ON ledger.source_type='UNIT_RECOVERY' AND ledger.source_id=recovery.lot_id
                WHERE (recovery.wallet_paid_amount=0 AND ledger.id IS NOT NULL)
                   OR (recovery.wallet_paid_amount>0 AND (
                        ledger.id IS NULL OR ledger.user_id<>recovery.user_id OR ledger.type<>'CREDIT'
                        OR ledger.paid_amount<>recovery.wallet_paid_amount))
                """);
    }

    private List<FinancialConsistencyFinding> payoutLedgerFindings() {
        return query("""
                SELECT 'PENDING_PAYOUT_LEDGER' AS code, payout.user_id, 'PENDING_PAYOUT' AS entity_type,
                       payout.id AS entity_id, payout.paid_amount::bigint AS expected_value,
                       ledger.paid_amount::bigint AS actual_value
                FROM pending_recovery_payouts payout
                LEFT JOIN point_ledgers ledger
                  ON ledger.source_type='PENDING_RECOVERY_PAYOUT' AND ledger.source_id=payout.id
                WHERE ledger.id IS NULL OR ledger.user_id<>payout.user_id OR ledger.type<>'CREDIT'
                   OR ledger.paid_amount<>payout.paid_amount
                """);
    }

    private List<FinancialConsistencyFinding> query(String sql) {
        return jdbc.query(sql, (resultSet, rowNumber) -> new FinancialConsistencyFinding(
                resultSet.getString("code"),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("entity_type"),
                resultSet.getObject("entity_id", UUID.class),
                nullableLong(resultSet, "expected_value"),
                nullableLong(resultSet, "actual_value")));
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
