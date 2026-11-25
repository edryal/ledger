-- Deposits, withdrawals and wallet-to-wallet transfers.
-- Owned by the transfers module. Account and entry IDs point into bookkeeping,
-- but there are no foreign keys across modules: each module owns its own tables.

CREATE TABLE transfers (
    id                      UUID        NOT NULL PRIMARY KEY,
    kind                    TEXT        NOT NULL,
    source_account_id       UUID,
    destination_account_id  UUID,
    amount                  BIGINT      NOT NULL,
    currency                CHAR(3)     NOT NULL,
    description             TEXT,
    journal_entry_id        UUID        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,

    CONSTRAINT transfers_kind_check CHECK (kind IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),

    CONSTRAINT transfers_amount_positive CHECK (amount > 0),

    -- Deposits come from outside (no source), withdrawals go outside (no destination).
    CONSTRAINT transfers_accounts_check CHECK (
        (kind = 'DEPOSIT'    AND source_account_id IS NULL     AND destination_account_id IS NOT NULL) OR
        (kind = 'WITHDRAWAL' AND source_account_id IS NOT NULL AND destination_account_id IS NULL) OR
        (kind = 'TRANSFER'   AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL
                             AND source_account_id <> destination_account_id)
    ),

    CONSTRAINT transfers_journal_entry_key UNIQUE (journal_entry_id)
);
