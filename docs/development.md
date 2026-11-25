# Development guide

## Ports

| What | Port |
|---|---|
| App | `8081` |
| PostgreSQL | `5433` |
| Debugger | `5005` |

## Run

```sh
./mvnw spring-boot:run                                    # app + Postgres
curl localhost:8081/actuator/health                       # {"status":"UP"}
```

Debug (then attach to `localhost:5005`):

```sh
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

> Launching from the IDE? Rebuild after changing `application.yaml`,
> otherwise the old config from `target/classes` is used.

## Database

```
postgresql://ledger:ledger@localhost:5433/ledger
jdbc:postgresql://localhost:5433/ledger
```

```sh
docker compose exec postgres psql -U ledger    # shell, no install needed
docker compose down                            # reset: deletes all data
```

| psql | |
|---|---|
| `\dt` | tables |
| `\d postings` | columns, constraints, triggers |
| `\x` | vertical output |
| `\q` | quit |

Queries: [`sql/inspect.sql`](sql/inspect.sql) (balances, entries, trial balance).

> `journal_entries` and `postings` are append-only: `DELETE`/`TRUNCATE` fail.
> Use `docker compose down` to clear them.

## Migrations

- `src/main/resources/db/migration/V<n>__<description>.sql`
- Never edit a pushed migration, add `V<n+1>` instead.
- Hibernate only validates (`ddl-auto: validate`).

## Tests

```sh
./mvnw test                           # all
./mvnw test -Dtest=JournalEntryTest   # one class
./mvnw test -Dtest='Money*,Posting*'  # pattern
```

Integration tests use their own throwaway Postgres, never the dev database.
