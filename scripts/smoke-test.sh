#!/usr/bin/env bash
set -u

NACOS_CONSOLE_URL="${NACOS_CONSOLE_URL:-http://localhost:18081/}"
USER_SERVICE_URL="${USER_SERVICE_URL:-http://localhost:8081}"
TEXTBOOK_SERVICE_URL="${TEXTBOOK_SERVICE_URL:-http://localhost:8082}"
ORDER_SERVICE_URL="${ORDER_SERVICE_URL:-http://localhost:8083}"

for command_name in curl grep; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "[FAIL] Required command not found: $command_name" >&2
    exit 1
  fi
done

check_nacos_console() {
  local status

  echo "[CHECK] Nacos console: $NACOS_CONSOLE_URL"

  if status=$(curl \
    --noproxy '*' \
    --silent \
    --show-error \
    --location \
    --connect-timeout 2 \
    --max-time 10 \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$NACOS_CONSOLE_URL"); then

    if [[ "$status" == "200" ]]; then
      echo "[PASS] Nacos console HTTP 200"
      return 0
    fi

    echo "[FAIL] Nacos console returned HTTP $status" >&2
  else
    echo "[FAIL] Nacos console connection failed" >&2
  fi

  return 1
}

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

check_nacos_console || failed=$((failed + 1))
check_health "user-service" "$USER_SERVICE_URL" || failed=$((failed + 1))
check_health "textbook-service" "$TEXTBOOK_SERVICE_URL" || failed=$((failed + 1))
check_health "order-service" "$ORDER_SERVICE_URL" || failed=$((failed + 1))

if [[ "$failed" -ne 0 ]]; then
  echo "[FAIL] $failed check(s) failed"
  exit 1
fi

echo "[PASS] Nacos console and all three services passed"
exit 0