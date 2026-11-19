#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

die() {
  echo "[FAIL] $*" >&2
  exit 1
}

pass() {
  echo "[PASS] $*"
}

for cmd in docker curl awk; do
  command -v "$cmd" >/dev/null 2>&1 ||
    die "Required command not found: $cmd"
done

PY=""
for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1 &&
     "$candidate" -c 'import json, uuid' >/dev/null 2>&1; then
    PY="$candidate"
    break
  fi
done
[[ -n "$PY" ]] || die "Python 3 is required"

export PYTHONIOENCODING=utf-8

docker compose version >/dev/null
docker compose config --quiet

new_id() {
  "$PY" -c 'import uuid; print(uuid.uuid4())'
}

TOKEN=$("$PY" -c 'import uuid; print(uuid.uuid4().hex[:16])')
ART="work/w08-e2e-$TOKEN"
mkdir -p "$ART"

TEXTBOOK_URL="${TEXTBOOK_SERVICE_URL:-http://localhost:8082}"
ORDER_URL="${ORDER_SERVICE_URL:-http://localhost:8083}"
TEXTBOOK_URL="${TEXTBOOK_URL%/}"
ORDER_URL="${ORDER_URL%/}"

restore_needed=0

cleanup() {
  local rc=$?
  trap - EXIT

  if [[ "$restore_needed" == 1 ]]; then
    echo "[CLEANUP] Restarting existing user-service containers"
    if ! docker compose start user-service; then
      echo "[FAIL] Restore failed; run docker compose start user-service" >&2
      rc=1
    fi
  fi

  echo "[INFO] Evidence directory: $ART"
  exit "$rc"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

field() {
  "$PY" - "$1" "$2" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8-sig") as f:
    value = json.load(f)[sys.argv[2]]
print(value)
PY
}

assert_eq() {
  [[ "$1" == "$2" ]] ||
    die "$3: expected=$2 actual=$1"
}

payload() {
  "$PY" - "$@" <<'PY'
import json, sys
kind = sys.argv[1]
if kind == "user":
    data = {
        "studentNo": sys.argv[2],
        "displayName": sys.argv[3]
    }
elif kind == "book":
    data = {
        "title": "E2E Test Book",
        "isbn": "e2e-" + sys.argv[3],
        "author": "Test Author",
        "publisher": "Test Publisher",
        "conditionDescription": "Almost new",
        "price": 25.50,
        "stock": 5,
        "sellerId": int(sys.argv[2])
    }
elif kind == "order":
    data = {
        "requestId": sys.argv[2],
        "buyerId": int(sys.argv[3]),
        "textbookId": int(sys.argv[4]),
        "quantity": int(sys.argv[5])
    }
else:
    raise SystemExit("Unknown payload kind")
print(json.dumps(data))
PY
}

RESP=""
HTTP_CODE=""

http() {
  local name="$1" method="$2" url="$3" body="${4:-}"
  local args=(
    --noproxy '*'
    --silent --show-error
    --connect-timeout 3 --max-time 20
    --request "$method"
    --header "Content-Type: application/json"
  )

  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  RESP="$ART/$name.json"

  if ! HTTP_CODE=$(curl "${args[@]}" \
      --output "$RESP" --write-out '%{http_code}' "$url"); then
    die "Transport failure: $name. A write result may be unknown. Preserve request file: $body"
  fi
}

expect_http() {
  if [[ "$HTTP_CODE" != "$1" ]]; then
    cat "$RESP" >&2
    die "Expected HTTP $1, got $HTTP_CODE"
  fi
}

wait_up() {
  local base="$1" name="$2"
  local deadline=$((SECONDS + 90))
  local file="$ART/health-$name.json"

  while (( SECONDS < deadline )); do
    if curl --noproxy '*' --fail --silent \
        --connect-timeout 2 --max-time 5 \
        "$base/actuator/health" -o "$file" &&
       [[ "$(field "$file" status)" == "UP" ]]; then
      return 0
    fi
    sleep 2
  done

  die "$name did not become healthy"
}

wait_users() {
  local index address
  for index in 1 2; do
    address=$(docker compose port --index "$index" user-service 8080 |
      tr -d '\r')
    [[ -n "$address" ]] || die "Missing user-service instance $index"
    wait_up "http://$address" "user-$index"
  done
}

sql_count() {
  local request_id="$1"
  local query="SELECT COUNT(*) FROM trade_orders WHERE request_id='$request_id';"

  MSYS_NO_PATHCONV=1 docker compose exec -T order-db sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql \
      --protocol=TCP -h 127.0.0.1 \
      -u"$MYSQL_USER" --database="$MYSQL_DATABASE" \
      --batch --skip-column-names --execute="$1"' \
    sh "$query" | tr -d '\r'
}

assert_stock() {
  local expected="$1" name="$2"
  http "$name" GET "$TEXTBOOK_URL/api/textbooks/$BOOK_ID"
  expect_http 200
  assert_eq "$(field "$RESP" stock)" "$expected" "$name stock"
}

echo "[INFO] E2E run=$TOKEN"

count=$(docker compose ps -q user-service |
  awk 'NF {n++} END {print n+0}')
assert_eq "$count" 2 "Running user-service instances"

wait_users
wait_up "$TEXTBOOK_URL" textbook
wait_up "$ORDER_URL" order

USER_ADDRESS=$(docker compose port --index 1 user-service 8080 |
  tr -d '\r')
USER_URL="http://$USER_ADDRESS"

# Each run creates independent data; existing user data is preserved.
payload user "e2e-s-$TOKEN" "E2E Seller" > "$ART/seller-request.json"
http seller POST "$USER_URL/api/users" "$ART/seller-request.json"
expect_http 201
SELLER_ID=$(field "$RESP" id)

payload user "e2e-b-$TOKEN" "E2E Buyer" > "$ART/buyer-request.json"
http buyer POST "$USER_URL/api/users" "$ART/buyer-request.json"
expect_http 201
BUYER_ID=$(field "$RESP" id)

payload book "$SELLER_ID" "$TOKEN" > "$ART/book-request.json"
http book POST "$TEXTBOOK_URL/api/textbooks" "$ART/book-request.json"
expect_http 201
BOOK_ID=$(field "$RESP" id)
assert_eq "$(field "$RESP" stock)" 5 "Initial stock"

RID=$(new_id)
payload order "$RID" "$BUYER_ID" "$BOOK_ID" 2 \
  > "$ART/order-request.json"

http order POST "$ORDER_URL/api/orders" "$ART/order-request.json"
expect_http 201
ORDER_ID=$(field "$RESP" id)
assert_eq "$(field "$RESP" status)" PENDING_PAYMENT "Order status"
assert_eq "$(field "$RESP" requestId)" "$RID" "Order requestId"
assert_eq "$(field "$RESP" quantity)" 2 "Order quantity"
assert_eq "$(sql_count "$RID")" 1 "Normal order database count"
assert_stock 3 normal-stock
pass "NORMAL_ORDER: HTTP=201, orderId=$ORDER_ID, stock=3, dbCount=1"

http order-query GET "$ORDER_URL/api/orders/$ORDER_ID"
expect_http 200
assert_eq "$(field "$RESP" id)" "$ORDER_ID" "Queried order id"
assert_eq "$(field "$RESP" requestId)" "$RID" "Queried requestId"
pass "ORDER_QUERY: HTTP=200"

http order-repeat POST "$ORDER_URL/api/orders" "$ART/order-request.json"
expect_http 201
assert_eq "$(field "$RESP" id)" "$ORDER_ID" "Repeated order id"
assert_eq "$(sql_count "$RID")" 1 "Repeated order database count"
assert_stock 3 repeated-stock
pass "IDEMPOTENT_RETRY: same orderId, stock=3, dbCount=1"

# New requestId ensures this request cannot return an old successful order.
FAULT_RID=$(new_id)
payload order "$FAULT_RID" "$BUYER_ID" "$BOOK_ID" 1 \
  > "$ART/fault-request.json"

# Stop every user-service replica, not just one.
restore_needed=1
docker compose stop user-service

http dependency-down POST "$ORDER_URL/api/orders" "$ART/fault-request.json"
expect_http 503
assert_eq "$(field "$RESP" code)" DEPENDENCY_UNAVAILABLE "Failure code"
[[ -n "$(field "$RESP" message)" ]] || die "Missing failure message"
assert_eq "$(sql_count "$FAULT_RID")" 0 "Failed request database count"
assert_stock 3 failed-stock
pass "DEPENDENCY_DOWN: HTTP=503, dbCount=0, stock=3"

# Start the same containers, preserving replica count and port mappings.
docker compose start user-service
wait_users
restore_needed=0

# Discovery may need time to refresh. Retry only with the original payload.
deadline=$((SECONDS + 120))
while :; do
  http recovered-order POST "$ORDER_URL/api/orders" "$ART/fault-request.json"

  if [[ "$HTTP_CODE" == 201 ]]; then
    break
  fi

  expect_http 503
  assert_eq "$(field "$RESP" code)" DEPENDENCY_UNAVAILABLE "Recovery wait code"
  (( SECONDS < deadline )) || die "Recovery timed out"
  sleep 2
done

RECOVERED_ID=$(field "$RESP" id)
assert_eq "$(field "$RESP" requestId)" "$FAULT_RID" "Recovered requestId"
assert_eq "$(field "$RESP" status)" PENDING_PAYMENT "Recovered status"
assert_eq "$(sql_count "$FAULT_RID")" 1 "Recovered database count"
assert_stock 2 recovered-stock
pass "RECOVERY: HTTP=201, orderId=$RECOVERED_ID, stock=2, dbCount=1"

http recovery-repeat POST "$ORDER_URL/api/orders" "$ART/fault-request.json"
expect_http 201
assert_eq "$(field "$RESP" id)" "$RECOVERED_ID" "Recovery repeated id"
assert_eq "$(sql_count "$FAULT_RID")" 1 "Recovery repeated database count"
assert_stock 2 recovery-repeated-stock
pass "RECOVERY_RETRY: same orderId, stock=2, dbCount=1"

pass "ALL E2E CHECKS PASSED"