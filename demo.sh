#!/usr/bin/env bash

# Walks through the ledger step by step.
# Uses the app if it's already running, otherwise starts it and stops it again at the end.
# Usage: ./demo.sh [base-url]
# Needs curl, jq and Docker.

set -euo pipefail

BASE_URL="${1:-http://localhost:8081}"
START_COMMAND=(./mvnw -q spring-boot:run)
LOG_FILE="demo.log"

cd "$(dirname "$0")"

step() { printf '\n\033[1m%s\033[0m\n' "$*"; }

fail() {
    printf '\033[31m%s\033[0m\n' "$*" >&2
    exit 1
}

is_up() { curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1; }

# post <path> <idempotency-key> <json>  -> prints the response body, keeps the status in $STATUS
post() {
    local response
    response=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL$1" \
        -H 'Content-Type: application/json' \
        ${2:+-H "Idempotency-Key: $2"} \
        -d "$3")
    STATUS=$(tail -n1 <<<"$response")
    sed '$d' <<<"$response"
}

open_wallet() {
    post /v1/accounts "" \
        "{\"type\": \"CUSTOMER_WALLET\", \"currency\": \"EUR\", \"owner_id\": \"$(uuidgen)\"}" | jq -r .id
}

# {"value": 7500, "currency": "EUR"} -> 75.00 EUR
euros() { jq -r '"\(.value) \(.currency)"' | awk '{ printf "%.2f %s", $1 / 100, $2 }'; }

balance() { curl -sf "$BASE_URL/v1/accounts/$1" | jq '.balance' | euros; }

APP_PID=""

stop_app() {
    if [[ -n "$APP_PID" ]]; then
        echo
        echo "Stopping the app"
        # The app runs in its own process group, so this also stops the JVM Maven started.
        kill -- "-$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
}
trap stop_app EXIT

for tool in curl jq uuidgen; do
    command -v "$tool" >/dev/null || fail "$tool is required but not installed"
done

if is_up; then
    echo "Using the app at $BASE_URL"
else
    echo "Starting the app, logs go to $LOG_FILE"
    setsid "${START_COMMAND[@]}" >"$LOG_FILE" 2>&1 &
    APP_PID=$!

    for _ in $(seq 1 90); do
        is_up && break
        kill -0 "$APP_PID" 2>/dev/null || fail "The app stopped while starting, see $LOG_FILE"
        sleep 2
    done
    is_up || fail "The app didn't start within 3 minutes, see $LOG_FILE"
    echo "App is up"
fi

step "1. Open two wallets"
catalin=$(open_wallet)
bob=$(open_wallet)
echo "Catalin: $catalin"
echo "Bob:     $bob"

step "2. Catalin deposits 100.00 EUR"
post /v1/deposits "demo-deposit-$catalin" \
    "{\"account_id\": \"$catalin\", \"amount\": {\"value\": 10000, \"currency\": \"EUR\"}, \"description\": \"Salary\"}" >/dev/null
echo "Catalin: $(balance "$catalin")"

step "3. Catalin sends Bob 25.00 EUR"
transfer="{\"source_account_id\": \"$catalin\", \"destination_account_id\": \"$bob\",
           \"amount\": {\"value\": 2500, \"currency\": \"EUR\"}, \"description\": \"Dinner\"}"
first=$(post /v1/transfers "demo-transfer-$catalin" "$transfer" | jq -r .id)
echo "Transfer $first created (HTTP $STATUS)"
echo "Catalin: $(balance "$catalin")   Bob: $(balance "$bob")"

step "4. The same request is sent again (e.g. after a timeout)"
second=$(post /v1/transfers "demo-transfer-$catalin" "$transfer" | jq -r .id)
echo "Got transfer $second back (HTTP $STATUS), same ID, nothing moved twice"
echo "Catalin: $(balance "$catalin")   Bob: $(balance "$bob")"

step "5. Catalin tries to send 500.00 EUR"
post /v1/transfers "demo-overspend-$catalin" \
    "{\"source_account_id\": \"$catalin\", \"destination_account_id\": \"$bob\",
      \"amount\": {\"value\": 50000, \"currency\": \"EUR\"}}" | jq -r '"HTTP \(.status): \(.detail)"'
echo "Catalin: $(balance "$catalin")   Bob: $(balance "$bob")"

step "6. Catalin's statement, newest first"
curl -sf "$BASE_URL/v1/accounts/$catalin/postings" |
    jq -r '.data[] | "\(.direction | ascii_downcase) \(.amount.value) \(.description)"' |
    awk '{ printf "%-7s %7.2f EUR  %s\n", $1, $2 / 100, $3 }'

step "7. Are the books consistent?"
curl -s "$BASE_URL/actuator/health/ledger" | jq -r '"Ledger health: \(.status)"'
