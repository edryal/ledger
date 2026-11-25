-- Queries for inspecting the development database.
-- Run them one at a time.

-- Applied migrations
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

-- All accounts with their balances (in minor units)
SELECT a.code, a.type, a.category, a.currency, b.balance, b.allow_negative
FROM accounts a
JOIN account_balances b ON b.account_id = a.id
ORDER BY a.currency, a.code NULLS LAST, a.created_at;

-- Latest journal entries with their postings
SELECT e.posted_at, e.reference, p.direction, p.amount, p.currency,
       coalesce(a.code, a.type || ' ' || a.id) AS account
FROM journal_entries e
JOIN postings p ON p.entry_id = e.id
JOIN accounts a ON a.id = p.account_id
ORDER BY e.posted_at DESC, e.id, p.direction DESC
LIMIT 50;

-- Entries that don't balance (should return no rows)
SELECT e.reference, p.currency,
       sum(CASE p.direction WHEN 'DEBIT' THEN p.amount ELSE -p.amount END) AS net
FROM journal_entries e
JOIN postings p ON p.entry_id = e.id
GROUP BY e.id, e.reference, p.currency
HAVING sum(CASE p.direction WHEN 'DEBIT' THEN p.amount ELSE -p.amount END) <> 0;

-- Trial balance per currency: total debits must equal total credits
SELECT currency,
       sum(amount) FILTER (WHERE direction = 'DEBIT')  AS debits,
       sum(amount) FILTER (WHERE direction = 'CREDIT') AS credits
FROM postings
GROUP BY currency;
