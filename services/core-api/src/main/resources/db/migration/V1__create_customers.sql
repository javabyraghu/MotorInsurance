CREATE TABLE customers (
    id uuid PRIMARY KEY,
    customer_number varchar(36) NOT NULL UNIQUE,
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    email varchar(254) NOT NULL,
    phone_number varchar(32),
    date_of_birth date NOT NULL,
    status varchar(8) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT customers_customer_number_format CHECK (customer_number ~ '^CUS-[0-9A-F]{32}$'),
    CONSTRAINT customers_first_name_nonblank CHECK (length(btrim(first_name)) > 0),
    CONSTRAINT customers_last_name_nonblank CHECK (length(btrim(last_name)) > 0),
    CONSTRAINT customers_email_nonblank CHECK (length(btrim(email)) > 0),
    CONSTRAINT customers_status_valid CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX customers_created_at_id_idx ON customers (created_at DESC, id ASC);
