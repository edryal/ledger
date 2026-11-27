# API

All endpoints are under `/v1`. JSON uses `snake_case`. Endpoints marked
*(planned)* don't exist yet.

## Money

Money is always an object with the amount in minor units:

```json
{ "value": 2599, "currency": "EUR" }
```

That's 25.99 EUR. In requests, `value` must be positive. The operation decides
which way the money moves.

## Endpoints

| Method | Path | What it does | Idempotency key |
|---|---|---|---|
| `POST` | `/v1/accounts` | open an account | - |
| `GET` | `/v1/accounts/{id}` | account with its balance | - |
| `GET` | `/v1/accounts/{id}/postings` | statement, newest first, paginated | - |
| `GET` | `/v1/journal-entries/{id}` | one entry with all its postings | - |
| `POST` | `/v1/deposits` | money comes in | required |
| `POST` | `/v1/withdrawals` | money goes out | required |
| `POST` | `/v1/transfers` | money moves between two accounts | required |
| `GET` | `/v1/transfers/{id}` | a deposit, withdrawal or transfer | - |
| `POST` | `/v1/merchants` *(planned)* | create a merchant | - |
| `POST` | `/v1/payments` *(planned)* | authorize a payment | required |
| `GET` | `/v1/payments/{id}` *(planned)* | a payment | - |
| `POST` | `/v1/payments/{id}/capture` *(planned)* | capture | required |
| `POST` | `/v1/payments/{id}/void` *(planned)* | void | required |
| `POST` | `/v1/payments/{id}/refunds` *(planned)* | refund all or part | required |
| `PUT` | `/v1/webhook-endpoint` *(planned)* | set the merchant's webhook URL | - |

## Examples

### Open an account

```http
POST /v1/accounts
Content-Type: application/json

{"type": "CUSTOMER_WALLET", "currency": "EUR", "owner_id": "6f1c2a9e-1b7d-4c55-9c1e-2f3d4b5a6c7d"}
```

```http
HTTP/1.1 201 Created
Location: /v1/accounts/ff97d58c-6b06-4503-bf0d-2006ef48b27f

{"id": "ff97d58c-6b06-4503-bf0d-2006ef48b27f", "balance": {"value": 0, "currency": "EUR"}}
```

`type` is `CUSTOMER_WALLET` or `MERCHANT_BALANCE`.

### Transfer

```http
POST /v1/transfers
Content-Type: application/json
Idempotency-Key: 6f1c2a9e-1b7d-4c55-9c1e-2f3d4b5a6c7d

{
  "source_account_id": "ff97d58c-...",
  "destination_account_id": "8dd0a4a0-...",
  "amount": {"value": 2500, "currency": "EUR"},
  "description": "Dinner"
}
```

```http
HTTP/1.1 201 Created
Location: /v1/transfers/d85a3f44-...

{
  "id": "d85a3f44-...",
  "kind": "TRANSFER",
  "source_account_id": "ff97d58c-...",
  "destination_account_id": "8dd0a4a0-...",
  "amount": {"value": 2500, "currency": "EUR"},
  "description": "Dinner",
  "journal_entry_id": "0193a1c4-...",
  "created_at": "2026-11-24T15:21:07.412345Z"
}
```

Deposits take `account_id` instead of the two account fields, and so do
withdrawals. Their responses leave out the missing side.

### Statement

```http
GET /v1/accounts/ff97d58c-.../postings?limit=2
```

```json
{
  "data": [
    {"posting_id": "...", "entry_id": "...", "reference": "transfer:d85a3f44-...",
     "description": "Dinner", "direction": "DEBIT",
     "amount": {"value": 2500, "currency": "EUR"}, "posted_at": "2026-11-24T15:21:07.412345Z"},
    {"posting_id": "...", "entry_id": "...", "reference": "transfer:4b1e...",
     "description": "Salary", "direction": "CREDIT",
     "amount": {"value": 10000, "currency": "EUR"}, "posted_at": "2026-11-24T15:20:51.100230Z"}
  ],
  "next_cursor": "MjAyNi0xMS0yNFQxNTo..."
}
```

- `limit` is 1 to 100, default 50.
- Pass `next_cursor` back as `?cursor=...` to get the next page. When it's
  missing, you've reached the end.
- Cursors are opaque. Don't build or change them yourself.

## Retries and idempotency keys

Requests that move money need an `Idempotency-Key` header (1 to 255
characters, a UUID works well).

| You send | You get |
|---|---|
| a new key | the request runs normally |
| the same key and the same body again | the first response again, with `Idempotent-Replayed: true` |
| the same key with a different body or endpoint | `422 idempotency-key-reused` |
| no key | `400 missing-idempotency-key` |

Failed requests aren't stored, so you can retry them with the same key. Keys
expire after 24 hours.

## Errors

Errors follow RFC 9457 Problem Details (`application/problem+json`):

```json
{
  "type": "/problems/insufficient-funds",
  "title": "Insufficient funds",
  "status": 422,
  "detail": "account ff97d58c-... has 75.00 EUR available, 500.00 EUR required"
}
```

| Status | Type | When |
|---|---|---|
| 400 | `missing-idempotency-key` | no or invalid `Idempotency-Key` |
| 400 | `invalid-cursor` | a `cursor` this API didn't produce |
| 400 | *(no type)* | invalid JSON, a missing field, a bad UUID, `limit` out of range |
| 404 | `not-found` | unknown account, entry or transfer |
| 409 | `duplicate-reference` | an entry with this reference was already posted |
| 422 | `insufficient-funds` | not enough money |
| 422 | `currency-mismatch` | the amount's currency isn't the account's |
| 422 | `unsupported-currency` | no system accounts for this currency |
| 422 | `same-account` | transfer source and destination are the same |
| 422 | `idempotency-key-reused` | key reused for a different request |

Planned: `401`/`403` once authentication exists, and `409 invalid-state-transition`,
`422 refund-exceeds-captured` and `422 amount-below-fee` for payments.

## Authentication *(planned)*

There's no authentication yet. Planned:

- **Operators** (back office): OAuth2 JWTs from Keycloak, with roles.
- **Merchants**: API keys (`Authorization: Bearer sk_test_...`), shown once and
  stored as a hash.

## Webhooks *(planned)*

Payment events will be sent to the merchant's URL:

- `payment.authorized`, `payment.declined`, `payment.captured`,
  `payment.voided`, `payment.expired`, `payment.refunded`

```http
POST https://merchant.example/hooks
Content-Type: application/json
Ledger-Signature: t=1764000000,v1=5f2b6c...

{
  "id": "0193a1d0-...",
  "type": "payment.captured",
  "created_at": "2026-11-24T15:30:00Z",
  "data": {
    "payment_id": "0193a1c9-...",
    "amount": {"value": 2599, "currency": "EUR"},
    "fee": {"value": 61, "currency": "EUR"},
    "net": {"value": 2538, "currency": "EUR"}
  }
}
```

- **Signature:** `v1 = hex(HMAC-SHA256(secret, t + "." + raw_body))`. Receivers
  recompute it, compare in constant time and reject timestamps older than
  5 minutes.
- **Delivery:** any `2xx` is success. Anything else is retried with exponential
  backoff and jitter (30s, 1m, 2m, 4m, ...) up to 10 times, about 8.5 hours.
  After that the delivery is marked `DEAD` and can be retried by hand.
- Delivery is at least once, so receivers should ignore an event `id` they've
  already seen.
