CREATE FUNCTION reject_point_ledger_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'point_ledgers is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reject_point_ledger_update_or_delete
    BEFORE UPDATE OR DELETE ON point_ledgers
    FOR EACH ROW
    EXECUTE FUNCTION reject_point_ledger_mutation();
