-- Ledger core: accounts, balances, journal entries and postings.
CREATE TABLE accounts (
    id          UUID        NOT NULL PRIMARY KEY,
    code        TEXT        UNIQUE,
    type        TEXT        NOT NULL,
    category    TEXT        NOT NULL,
    currency    CHAR(3)     NOT NULL,
    owner_id    UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT accounts_type_check
        CHECK (type IN ('CUSTOMER_WALLET', 'MERCHANT_BALANCE', 'PLATFORM_CASH', 'PLATFORM_HOLDS', 'PLATFORM_FEES')),

    CONSTRAINT accounts_category_check
        CHECK (category IN ('ASSET', 'LIABILITY', 'REVENUE')),

    CONSTRAINT accounts_id_currency_key UNIQUE (id, currency)
);

CREATE TABLE account_balances (
    account_id      UUID        NOT NULL PRIMARY KEY REFERENCES accounts (id),
    balance         BIGINT      NOT NULL DEFAULT 0,
    allow_negative  BOOLEAN     NOT NULL DEFAULT false,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Even if the application skips its check, a balance can't go negative.
    CONSTRAINT account_balances_non_negative
        CHECK (allow_negative OR balance >= 0)
);

CREATE TABLE journal_entries (
    id           UUID        NOT NULL PRIMARY KEY,
    reference    TEXT        NOT NULL,
    description  TEXT,
    posted_at    TIMESTAMPTZ NOT NULL,

    -- Rule 6: the same business event can't be posted twice.
    CONSTRAINT journal_entries_reference_key UNIQUE (reference)
);

CREATE TABLE postings (
    id          UUID        NOT NULL PRIMARY KEY,
    entry_id    UUID        NOT NULL REFERENCES journal_entries (id),
    account_id  UUID        NOT NULL,
    direction   TEXT        NOT NULL,
    amount      BIGINT      NOT NULL,
    currency    CHAR(3)     NOT NULL,
    posted_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT postings_direction_check CHECK (direction IN ('DEBIT', 'CREDIT')),

    -- Rule 2: amounts are strictly positive, direction says which way money moves.
    CONSTRAINT postings_amount_positive CHECK (amount > 0),

    -- Rule 4: a posting's currency must be its account's currency.
    CONSTRAINT postings_account_fk
        FOREIGN KEY (account_id, currency) REFERENCES accounts (id, currency)
);

-- Account statements: all postings of one account, newest first.
CREATE INDEX postings_account_statement_idx ON postings (account_id, posted_at DESC, id DESC);

-- Loading the postings of one entry.
CREATE INDEX postings_entry_idx ON postings (entry_id);

-- Rule 5: journal entries and postings are append-only.
CREATE FUNCTION reject_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER journal_entries_append_only
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER journal_entries_no_truncate
    BEFORE TRUNCATE ON journal_entries
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();

CREATE TRIGGER postings_append_only
    BEFORE UPDATE OR DELETE ON postings
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER postings_no_truncate
    BEFORE TRUNCATE ON postings
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();


-- System accounts, one set per supported currency.
WITH system_accounts (code, type, category, currency) AS (
    SELECT 'platform.' || kind.name || '.' || ccy.code, kind.type, kind.category, ccy.code
    FROM (VALUES ('EUR'), ('USD'), ('RON')) AS ccy (code)
    CROSS JOIN (VALUES ('cash',  'PLATFORM_CASH',  'ASSET'),
                       ('holds', 'PLATFORM_HOLDS', 'LIABILITY'),
                       ('fees',  'PLATFORM_FEES',  'REVENUE')) AS kind (name, type, category)
),
inserted AS (
    INSERT INTO accounts (id, code, type, category, currency)
    SELECT gen_random_uuid(), code, type, category, currency
    FROM system_accounts
    RETURNING id
)
INSERT INTO account_balances (account_id)
SELECT id FROM inserted;
