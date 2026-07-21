CREATE FUNCTION reject_idea_version_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'idea_versions is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reject_idea_version_update_or_delete
    BEFORE UPDATE OR DELETE ON idea_versions
    FOR EACH ROW
    EXECUTE FUNCTION reject_idea_version_mutation();
