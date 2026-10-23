CREATE TABLE quote_coverage_snapshots (
    id uuid PRIMARY KEY,
    quote_id uuid NOT NULL,
    source_product_version_id uuid NOT NULL,
    coverage_code varchar(64) NOT NULL,
    display_name varchar(120) NOT NULL,
    description varchar(2000),
    coverage_type varchar(32) NOT NULL,
    is_mandatory boolean NOT NULL,
    is_optional boolean NOT NULL,
    is_available boolean NOT NULL,
    snapshot_created_at timestamptz NOT NULL,
    CONSTRAINT quote_snapshot_version_fk FOREIGN KEY (quote_id, source_product_version_id)
        REFERENCES quotes(id, product_version_id) ON DELETE CASCADE,
    CONSTRAINT quote_coverage_snapshot_quote_code_unique UNIQUE (quote_id, coverage_code),
    CONSTRAINT quote_coverage_snapshot_type_valid CHECK (coverage_type IN ('THIRD_PARTY_LIABILITY', 'OWN_DAMAGE', 'PERSONAL_ACCIDENT', 'ROADSIDE_ASSISTANCE', 'OTHER')),
    CONSTRAINT quote_coverage_snapshot_mandatory_xor_optional CHECK (is_mandatory <> is_optional)
);

CREATE INDEX quote_coverage_snapshots_source_version_idx ON quote_coverage_snapshots (source_product_version_id);
