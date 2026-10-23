CREATE TABLE products (
    id uuid PRIMARY KEY,
    product_code varchar(32) NOT NULL UNIQUE,
    name varchar(160) NOT NULL,
    description varchar(2000),
    product_type varchar(32) NOT NULL,
    status varchar(8) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT products_code_format CHECK (product_code ~ '^[A-Z][A-Z0-9_-]{1,31}$'),
    CONSTRAINT products_name_nonblank CHECK (length(btrim(name)) > 0),
    CONSTRAINT products_type_valid CHECK (product_type IN ('THIRD_PARTY', 'COMPREHENSIVE', 'OTHER')),
    CONSTRAINT products_status_valid CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

CREATE INDEX products_created_at_id_idx ON products (created_at DESC, id ASC);
CREATE INDEX products_status_type_idx ON products (status, product_type);
