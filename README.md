# ledger

A double-entry ledger with a payments API on top, built as a modular monolith
with Java 25, Spring Boot 4, Spring Modulith and PostgreSQL. People keep money in
wallets and send it to each other, and soon they'll be able to pay shops too.
Every movement of money is recorded in an append-only journal, so every balance
can be explained and audited.

## Demo

You need JDK 25, Docker, `curl` and `jq`. Run:

```sh
./demo.sh
```

If the app isn't running yet, the script starts it, runs the demo and stops it
again. It opens two wallets and goes through a few situations:

```
Starting the app, logs go to demo.log
App is up

1. Open two wallets
Catalin: cc5540ff-9b20-4c06-b91e-16aa4a2c7d6e
Bob:     26302b71-41ca-4208-83f8-adac9aeb7c54

2. Catalin deposits 100.00 EUR
Catalin: 100.00 EUR

3. Catalin sends Bob 25.00 EUR
Transfer 498767f9-1c7c-4684-9c3e-f62b7be3635b created (HTTP 201)
Catalin: 75.00 EUR   Bob: 25.00 EUR

4. The same request is sent again (e.g. after a timeout)
Got transfer 498767f9-1c7c-4684-9c3e-f62b7be3635b back (HTTP 201), same ID, nothing moved twice
Catalin: 75.00 EUR   Bob: 25.00 EUR

5. Catalin tries to send 500.00 EUR
HTTP 422: account cc5540ff-9b20-4c06-b91e-16aa4a2c7d6e has 75.00 EUR available, 500.00 EUR required
Catalin: 75.00 EUR   Bob: 25.00 EUR

6. Catalin's statement, newest first
debit     25.00 EUR  Dinner
credit   100.00 EUR  Salary

7. Are the books consistent?
Ledger health: UP

Stopping the app
```

## How it works

- **Money only moves, it's never created or lost.** A deposit moves it from the
  platform's cash account into a wallet, a transfer from one wallet to another.
  Each move is a journal entry whose debits and credits add up to the same
  amount, which is what double-entry bookkeeping means.
- **Balances can always be checked.** A wallet's balance is the sum of its
  postings. It's also stored for fast reads, and an integrity check behind the
  health endpoint makes sure the two always match (step 7).
- **No double spending.** While money moves, the balances involved are locked
  (`SELECT ... FOR UPDATE`), so concurrent payments on the same wallet run one
  after the other. A payment that would take a wallet below zero is rejected and
  nothing is written (step 5).
- **Retries are safe.** Requests that move money carry an `Idempotency-Key`. If
  the same request arrives again, it gets the stored response back instead of
  running twice (step 4).

Never heard of debits and credits? [docs/concepts.md](docs/concepts.md) explains
them with examples.

## Status

| Part | Status |
|---|---|
| Wallets, journal entries, statements, integrity checks | done |
| Deposits, withdrawals, transfers, idempotency keys | done |
| Merchants and card-style payments (authorize, capture, void, refund) | next |
| Signed webhooks with retries | planned |
| Authentication (Keycloak, API keys) and OpenAPI docs | planned |

## Documentation

| I want to... | Read |
|---|---|
| understand debits, credits and the example flows | [docs/concepts.md](docs/concepts.md) |
| know the exact rules (money, rounding, payments) | [docs/rules.md](docs/rules.md) |
| see how the code is organized and why | [docs/architecture.md](docs/architecture.md) |
| use the API (endpoints, errors) | [docs/api.md](docs/api.md) |
| run, debug or look into the database | [docs/development.md](docs/development.md) |

## FAQ

**Why record entries instead of just updating a balance?**
A single balance column can't explain itself. If it's ever wrong, there's no way
to find out how it got there. With an append-only journal, every balance is the
sum of entries you can list, and mistakes are fixed with a reversal entry
instead of an edit, so the history stays complete.

**Why are amounts whole numbers like `2500`?**
They're minor units (cents), so `2500` is 25.00 EUR. A `long` is exact, fast and
easy to compare. Floating-point types like `double` can't store most decimal
fractions exactly, and `BigDecimal` is only used where rounding is needed, such
as fee calculations.

**How do concurrent payments avoid deadlocks?**
Every transaction locks the balance rows it needs in the same order, sorted by
account ID. Two transfers between the same wallets in opposite directions then
wait for each other instead of each holding one lock and waiting for the other.
A test runs hundreds of transfers on parallel threads and checks that no balance
goes negative and no money disappears.

**What if a request fails halfway?**
Posting the entry, updating the balances, saving the transfer and storing the
idempotency key all happen in one database transaction. Either everything is
saved or nothing is, so a retry after a failure starts from a clean state.

**Why one application instead of microservices?**
Moving money and recording why it moved must succeed or fail together, in one
ACID transaction. A modular monolith gives that without distributed
transactions or sagas. Spring Modulith keeps the modules separate (a test fails
if one reaches into another's internals), and side effects like webhooks go
through events, so they could become their own services later.

**Why plain SQL instead of JPA for the ledger?**
Ledger tables are append-only and the critical query is a locking `SELECT` in a
specific order. Plain SQL through `JdbcClient` keeps that explicit. JPA is a
better fit for objects that change over time, so it's planned for payments,
which move through states like authorized and captured.

**What stops bad data if the code has a bug?**
The database itself. Triggers reject any `UPDATE` or `DELETE` on journal
entries, a constraint blocks negative balances, a posting can't use a different
currency than its account, and each entry has a unique reference, so the same
business event can't be posted twice.
