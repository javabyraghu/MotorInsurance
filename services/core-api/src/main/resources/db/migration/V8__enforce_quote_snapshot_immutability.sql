ALTER TABLE quote_coverage_snapshots
    DROP CONSTRAINT quote_snapshot_version_fk,
    ADD CONSTRAINT quote_snapshot_version_fk FOREIGN KEY (quote_id, source_product_version_id)
        REFERENCES quotes(id, product_version_id) ON DELETE RESTRICT;

CREATE FUNCTION reject_quote_coverage_snapshot_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Quote coverage snapshots are immutable'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER quote_coverage_snapshots_immutable
    BEFORE UPDATE OR DELETE ON quote_coverage_snapshots
    FOR EACH ROW EXECUTE FUNCTION reject_quote_coverage_snapshot_mutation();
