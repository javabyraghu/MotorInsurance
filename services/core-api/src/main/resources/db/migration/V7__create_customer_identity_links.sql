CREATE TABLE customer_identity_links (
    id uuid PRIMARY KEY,
    issuer text NOT NULL,
    subject text NOT NULL,
    customer_id uuid NOT NULL,
    linked_at timestamptz NOT NULL,
    linked_by text NOT NULL,
    CONSTRAINT customer_identity_links_identity_unique UNIQUE (issuer, subject),
    CONSTRAINT customer_identity_links_customer_fk FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT customer_identity_links_issuer_nonblank CHECK (length(btrim(issuer)) > 0),
    CONSTRAINT customer_identity_links_subject_nonblank CHECK (length(btrim(subject)) > 0),
    CONSTRAINT customer_identity_links_linked_by_nonblank CHECK (length(btrim(linked_by)) > 0)
);

CREATE INDEX customer_identity_links_customer_id_idx ON customer_identity_links (customer_id);
