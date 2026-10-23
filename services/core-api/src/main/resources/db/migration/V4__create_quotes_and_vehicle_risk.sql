ALTER TABLE product_versions
    ADD CONSTRAINT product_versions_product_id_id_unique UNIQUE (product_id, id);

CREATE TABLE quotes (
    id uuid PRIMARY KEY,
    quote_number varchar(40) NOT NULL,
    customer_id uuid NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL,
    product_version_id uuid NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'DRAFT',
    pricing_status varchar(32) NOT NULL DEFAULT 'NOT_CALCULATED',
    coverage_effective_at timestamptz NOT NULL,
    expires_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT quotes_number_unique UNIQUE (quote_number),
    CONSTRAINT quotes_product_version_fk FOREIGN KEY (product_id, product_version_id)
        REFERENCES product_versions(product_id, id) ON DELETE RESTRICT,
    CONSTRAINT quotes_id_version_unique UNIQUE (id, product_version_id),
    CONSTRAINT quotes_status_valid CHECK (status IN ('DRAFT', 'PENDING_UNDERWRITING', 'CANCELLED')),
    CONSTRAINT quotes_pricing_status_valid CHECK (pricing_status = 'NOT_CALCULATED'),
    CONSTRAINT quotes_expiry_after_create CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX quotes_customer_created_idx ON quotes (customer_id, created_at DESC, id);
CREATE INDEX quotes_status_created_idx ON quotes (status, created_at DESC, id);

CREATE TABLE quote_vehicle_risk (
    quote_id uuid PRIMARY KEY REFERENCES quotes(id) ON DELETE CASCADE,
    vehicle_category varchar(32) NOT NULL,
    manufacturer varchar(120) NOT NULL,
    model varchar(120) NOT NULL,
    manufacture_year integer NOT NULL,
    registration_number varchar(32),
    fuel_type varchar(16),
    usage_category varchar(24) NOT NULL,
    declared_vehicle_value numeric(14,2),
    declared_vehicle_currency varchar(3),
    CONSTRAINT quote_vehicle_category_valid CHECK (vehicle_category IN ('CAR', 'MOTORCYCLE', 'COMMERCIAL_VEHICLE', 'OTHER')),
    CONSTRAINT quote_vehicle_fuel_valid CHECK (fuel_type IS NULL OR fuel_type IN ('PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID', 'OTHER')),
    CONSTRAINT quote_vehicle_usage_valid CHECK (usage_category IN ('PRIVATE', 'COMMERCIAL', 'OTHER')),
    CONSTRAINT quote_vehicle_year_valid CHECK (manufacture_year BETWEEN 1886 AND 9999),
    CONSTRAINT quote_vehicle_value_positive CHECK (declared_vehicle_value IS NULL OR declared_vehicle_value > 0),
    CONSTRAINT quote_vehicle_value_currency_pair CHECK ((declared_vehicle_value IS NULL) = (declared_vehicle_currency IS NULL)),
    CONSTRAINT quote_vehicle_currency_format CHECK (declared_vehicle_currency IS NULL OR declared_vehicle_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT quote_vehicle_manufacturer_nonblank CHECK (length(btrim(manufacturer)) > 0),
    CONSTRAINT quote_vehicle_model_nonblank CHECK (length(btrim(model)) > 0)
);
