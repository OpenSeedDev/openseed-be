CREATE TABLE financial_consistency_checks (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    finding_count INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_financial_consistency_checks_status
        CHECK (status IN ('CONSISTENT', 'INCONSISTENT')),
    CONSTRAINT ck_financial_consistency_checks_count CHECK (finding_count >= 0),
    CONSTRAINT ck_financial_consistency_checks_time CHECK (completed_at >= started_at)
);

CREATE TABLE financial_consistency_findings (
    id UUID PRIMARY KEY,
    check_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    user_id UUID,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID,
    expected_value BIGINT,
    actual_value BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_consistency_findings_check
        FOREIGN KEY (check_id) REFERENCES financial_consistency_checks (id)
);

CREATE INDEX idx_financial_consistency_checks_completed_at
    ON financial_consistency_checks (completed_at DESC);
CREATE INDEX idx_financial_consistency_findings_check
    ON financial_consistency_findings (check_id, code);
CREATE INDEX idx_financial_consistency_findings_entity
    ON financial_consistency_findings (entity_type, entity_id);

CREATE FUNCTION reject_financial_consistency_audit_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'financial consistency audit is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reject_financial_consistency_check_update_or_delete
    BEFORE UPDATE OR DELETE ON financial_consistency_checks
    FOR EACH ROW EXECUTE FUNCTION reject_financial_consistency_audit_change();

CREATE TRIGGER reject_financial_consistency_finding_update_or_delete
    BEFORE UPDATE OR DELETE ON financial_consistency_findings
    FOR EACH ROW EXECUTE FUNCTION reject_financial_consistency_audit_change();
