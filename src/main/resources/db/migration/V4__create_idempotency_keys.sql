-- Idempotency keys for money-moving requests. A row is written in the same
-- transaction as the business change, so it exists only if that change committed.

CREATE TABLE idempotency_keys (
    client_id          TEXT        NOT NULL,
    idempotency_key    TEXT        NOT NULL,
    request_hash       BYTEA       NOT NULL,
    response_status    INT,
    response_location  TEXT,
    response_body      JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (client_id, idempotency_key)
);

CREATE INDEX idempotency_keys_created_at_idx ON idempotency_keys (created_at);
