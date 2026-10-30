#!/usr/bin/env bash
set -u

USER_SERVICE_URL="${USER_SERVICE_URL:-http://localhost:8081}"
TEXTBOOK_SERVICE_URL="${TEXTBOOK_SERVICE_URL:-http://localhost:8082}"
ORDER_SERVICE_URL="${ORDER_SERVICE_URL:-http://localhost:8083}"

for command_name in curl grep; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "[FAIL] Required command not found: $command_name" >&2
    exit 1
  fi
done

check_health() {
  local service_name="$1"
  local base_url="$2"
  local url="${base_url%/}/actuator/health"
  local response=""
  local status=""
  local body=""
  local attempt

  echo "[CHECK] $service_name: $url"

  for attempt in 1 2 3 4 5 6; do
    if response=$(curl \
      --noproxy '*' \
      --silent \
      --show-error \
      --connect-timeout 2 \
      --max-time 5 \
      --write-out $'\n%{http_code}' \
      "$url"); then

      status="${response##*$'\n'}"
      body="${response%$'\n'*}"

      if [[ "$status" == "200" ]] &&
        grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$body"; then
        echo "[PASS] $service_name HTTP 200, status=UP"
        return 0
      fi

      echo "[WAIT] attempt=$attempt HTTP=$status body=$body"
    else
      echo "[WAIT] attempt=$attempt connection failed"
    fi

    if [[ "$attempt" -lt 6 ]]; then
      sleep 2
    fi
  done

  echo "[FAIL] $service_name health check failed" >&2
  return 1
}

failed=0

check_health "user-service" "$USER_SERVICE_URL" || failed=$((failed + 1))
check_health "textbook-service" "$TEXTBOOK_SERVICE_URL" || failed=$((failed + 1))
check_health "order-service" "$ORDER_SERVICE_URL" || failed=$((failed + 1))

if [[ "$failed" -ne 0 ]]; then
  echo "[FAIL] $failed service(s) failed"
  exit 1
fi

echo "[PASS] All three services are healthy"
exit 0