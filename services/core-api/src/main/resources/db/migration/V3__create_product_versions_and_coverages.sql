CREATE TABLE product_versions (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    version_number integer NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    status varchar(8) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT product_versions_number_positive CHECK (version_number > 0),
    CONSTRAINT product_versions_product_number_unique UNIQUE (product_id, version_number),
    CONSTRAINT product_versions_effective_range_valid CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT product_versions_status_valid CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

CREATE INDEX product_versions_product_status_effective_idx
    ON product_versions (product_id, status, effective_from, effective_to);

CREATE TABLE product_version_coverages (
    id uuid PRIMARY KEY,
    product_version_id uuid NOT NULL REFERENCES product_versions(id) ON DELETE CASCADE,
    coverage_code varchar(64) NOT NULL,
    display_name varchar(120) NOT NULL,
    description varchar(2000),
    coverage_type varchar(32) NOT NULL,
    is_mandatory boolean NOT NULL,
    is_optional boolean NOT NULL,
    is_available boolean NOT NULL DEFAULT true,
    CONSTRAINT product_coverages_code_format CHECK (coverage_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT product_coverages_name_nonblank CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT product_coverages_type_valid CHECK (coverage_type IN ('THIRD_PARTY_LIABILITY', 'OWN_DAMAGE', 'PERSONAL_ACCIDENT', 'ROADSIDE_ASSISTANCE', 'OTHER')),
    CONSTRAINT product_coverages_mandatory_xor_optional CHECK (is_mandatory <> is_optional),
    CONSTRAINT product_coverages_mandatory_available CHECK (NOT is_mandatory OR is_available),
    CONSTRAINT product_coverages_version_code_unique UNIQUE (product_version_id, coverage_code)
);

CREATE INDEX product_coverages_type_available_idx
    ON product_version_coverages (coverage_type, is_available);
