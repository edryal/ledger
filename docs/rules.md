# Rules

The exact rules the ledger follows. Rules marked *(planned)* apply once that
part is built.

## Money

- Amounts are a **`long` count of minor units** (cents) plus an **ISO 4217
  currency**: `2599 EUR` means 25.99 EUR. Never `double` or `float`.
- The number of decimals comes from the currency: EUR and RON have 2, JPY has 0.
- A currency is **supported** if the platform has system accounts for it, which
  a migration creates. Today that's EUR, USD and RON. Anything else is refused
  with `422 unsupported-currency`.
- Amounts in different currencies are never added or compared. There is no
  currency conversion.
- Overflow throws instead of wrapping around.
- Rounding only happens when calculating fees *(planned)*: `HALF_EVEN` to the
  currency's minor unit, computed with `BigDecimal`.

## Ledger

1. An entry has **at least two postings**.
2. Every posting amount is **positive**. The direction says which way the money
   moves.
3. For each currency in an entry, **debits equal credits**.
4. A posting's currency must be its account's currency, otherwise
   `422 currency-mismatch`.
5. Entries and postings are **never changed or deleted**. Database triggers
   refuse `UPDATE`, `DELETE` and `TRUNCATE`. Mistakes are fixed with a new
   reversal entry.
6. Every entry has a unique **reference**, such as `transfer:<id>`. Posting the
   same reference twice is refused, so a business event can't be booked twice.
7. Customer and merchant accounts can't go **below zero**. An entry that would
   cause that is refused as a whole, and nothing is written.

The application checks rules 1 to 7. The database checks 2, 4, 5, 6 and 7 again
on its own, as a safety net.

## Transfers

- A **deposit** books Dr platform cash, Cr the account.
- A **withdrawal** books Dr the account, Cr platform cash.
- A **transfer** books Dr the source, Cr the destination. Source and destination
  must differ.
- A transfer is only saved once its entry is posted. A refused transfer leaves
  nothing behind.

## Idempotency

- `POST /v1/deposits`, `/v1/withdrawals` and `/v1/transfers` require an
  `Idempotency-Key` header of 1 to 255 characters.
- Keys are unique per client. Until authentication exists, all requests count
  as one client.
- The same key with the same request returns the stored response with an extra
  `Idempotent-Replayed: true` header.
- The same key with a different request, or on a different endpoint, gets
  `422 idempotency-key-reused`.
- Only successful requests are stored. A failed request, such as one with
  insufficient funds, leaves no key, so a retry runs again.
- Keys are deleted 24 hours after they were created.

## Payments *(planned)*

- Fee = `percentage x amount`, rounded `HALF_EVEN`, `+ fixed`, from the
  merchant's fee schedule. It's calculated and stored when the payment is
  authorized, so changing the schedule later never affects existing payments.
- An authorization whose amount is not more than its fee is refused.
- Only full captures. Refunds can be partial, but their total can never exceed
  the captured amount.
- Refunds don't return the fee. The merchant pays it.
- Authorizations not captured within **7 days** expire and the money goes back
  to the customer.

## Time and identifiers

- All timestamps are UTC: `Instant` in Java, `timestamptz` in PostgreSQL, ISO
  8601 with `Z` in JSON.
- Timestamps are stored to the microsecond, which is what PostgreSQL keeps.
- IDs are random UUIDs, created in Java so an object has its ID before it's
  saved. They're unguessable, so they're safe in URLs. Sorting always uses
  timestamps, never IDs.
- In Java, IDs are typed records (`AccountId`, `TransferId`, ...), never a bare
  `UUID` or `String`.
