#!/usr/bin/env bash
# Week 10: verify remote configuration is consumed by real service components.
# Password: secure interactive prompt, or AUTH_TEST_PASSWORD from CI secrets.
set +x
set -Eeuo pipefail
umask 077

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
AUTH_TEST_USERNAME="${AUTH_TEST_USERNAME:-demo-user}"
password="${AUTH_TEST_PASSWORD-}"
unset AUTH_TEST_PASSWORD
token=''
temp_dir=''
step='SETUP'

fail() { printf '[FAIL] %s\n' "$*" >&2; exit 1; }
pass() { printf '[PASS] %s\n' "$*"; }
cleanup() {
    password=''; token=''
    if [[ -n "$temp_dir" && -d "$temp_dir" ]]; then
                rm -f -- "$temp_dir/helper.py" "$temp_dir/login.json" \
                    "$temp_dir/wrong-login.json" "$temp_dir/response.json" \
                    "$temp_dir/response.headers" "$temp_dir/bearer.conf" \
                    "$temp_dir/baseline.json"
        rmdir -- "$temp_dir" 2>/dev/null || true
    fi
}
trap cleanup EXIT
trap 'fail "$step: unexpected command failure"' ERR
trap 'exit 130' INT
trap 'exit 143' TERM

for dependency in curl mktemp; do
    command -v "$dependency" >/dev/null 2>&1 || fail "SETUP: missing $dependency"
done
PYTHON=''
for candidate in python python3; do
    if command -v "$candidate" >/dev/null 2>&1 && \
       "$candidate" -c 'import json, secrets, sys; assert sys.version_info.major == 3' >/dev/null 2>&1; then
        PYTHON="$candidate"
        break
    fi
done
[[ -n "$PYTHON" ]] || fail 'SETUP: Python 3 is required for JSON parsing'
[[ "$BASE_URL" == http://* || "$BASE_URL" == https://* ]] || fail 'SETUP: BASE_URL must use http:// or https://'

# Explicitly convert file paths for native Windows curl/Python under Git Bash.
native_path() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi
}
temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/week10-config-test.XXXXXXXX")
cat > "$temp_dir/helper.py" <<'PY'
import json, re, secrets, sys
from pathlib import Path

def load():
    return json.loads(Path(sys.argv[2]).read_text(encoding='utf-8-sig'))

try:
    action = sys.argv[1]
    if action in ('login', 'wrong-login'):
        secret = (sys.stdin.buffer.read().decode('utf-8') if action == 'login'
                  else secrets.token_urlsafe(36))
        data = json.dumps({'username': sys.argv[2], 'password': secret}, ensure_ascii=False)
        sys.stdout.buffer.write(data.encode('utf-8'))
    elif action == 'token':
        data = load()
        roles = data.get('roles', [])
        assert isinstance(roles, list) and 'USER' in roles and 'ADMIN' not in roles
        assert data.get('username') == sys.argv[3]
        assert data.get('tokenType') == 'Bearer'
        assert isinstance(data.get('expiresIn'), (int, float)) and data['expiresIn'] > 0
        token = data.get('accessToken', '')
        assert re.fullmatch(r'[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', token)
        sys.stdout.write(token)
    elif action == 'me':
        data = load()
        assert data.get('username') == sys.argv[3]
        roles = data.get('roles', [])
        assert isinstance(roles, list) and 'USER' in roles and 'ADMIN' not in roles
    elif action == 'enforcer':
        lines = Path(sys.argv[2]).read_text(encoding='iso-8859-1').splitlines()
        assert any(line.partition(':')[0].lower() == 'x-auth-enforcer'
                   and line.partition(':')[2].strip() == 'gateway' for line in lines)
    elif action == 'proof':
        data = load()
        expected = int(sys.argv[4])
        assert data['service'] == sys.argv[3]
        assert data['marker'] == sys.argv[5]
        assert data['profile'] == sys.argv[6]
        assert data['configuredMaxPoolSize'] == data['actualMaxPoolSize'] == expected
        assert data['actualMinimumIdle'] == 2
        fields = ('service', 'profile', 'marker', 'configuredMaxPoolSize', 'actualMaxPoolSize', 'actualMinimumIdle')
        print('[INFO] CONFIG_READ ' + json.dumps({k: data[k] for k in fields}, ensure_ascii=True))
    elif action in ('dynamic-before', 'dynamic-ready', 'dynamic-after'):
        d = load()

        if action == 'dynamic-ready':
            sys.exit(
                0 if d.get('threshold') == 10
                and d.get('revision') == 'v2' else 2
            )

        assert d['service'] == 'textbook-service'
        assert d['profile'] == sys.argv[3]
        assert d['stock'] == 5
        assert d['pid'] > 0 and d['startedAt'] > 0 and d['instance']

        expected = 3 if action == 'dynamic-before' else 10
        revision = 'v1' if action == 'dynamic-before' else 'v2'

        assert d['threshold'] == expected
        assert d['revision'] == revision
        assert d['warning'] is (d['stock'] <= d['threshold'])

        if action == 'dynamic-after':
            old = json.loads(
                Path(sys.argv[4]).read_text(encoding='utf-8-sig')
            )
            assert all(
                d[k] == old[k]
                for k in ('instance', 'pid', 'startedAt')
            )

        fields = (
            'service', 'profile', 'instance', 'pid', 'startedAt',
            'stock', 'threshold', 'warning', 'revision'
        )
        print(
            '[INFO] ' + action.upper() + ' '
            + json.dumps({k: d[k] for k in fields})
        )
    elif action == 'tamper':
        parts = sys.stdin.read().strip().split('.')
        parts[2] = ('B' if parts[2][0] == 'A' else 'A') + parts[2][1:]
        sys.stdout.write('.'.join(parts))
    else:
        raise ValueError('unsupported action')
except Exception:
    # Never print response bodies, passwords, token values, or Python locals.
    print('[FAIL] JSON/identity/header assertion failed: ' + sys.argv[1], file=sys.stderr)
    sys.exit(1)
PY

json_tool() { "$PYTHON" "$(native_path "$temp_dir/helper.py")" "$@"; }
request() {
    local method="$1" path="$2" body_file="${3-}" use_token="${4-no}" spoof="${5-no}"
    local -a options=(--silent --show-error --connect-timeout 5 --max-time 20
        --retry 0 --noproxy '*' --request "$method" --url "$BASE_URL$path"
        --header 'Accept: application/json'
        --dump-header "$(native_path "$temp_dir/response.headers")"
        --output "$(native_path "$temp_dir/response.json")" --write-out '%{http_code}')
    if [[ -n "$body_file" ]]; then
        options+=(--header 'Content-Type: application/json' --data-binary "@$(native_path "$body_file")")
    fi
    if [[ "$use_token" == yes ]]; then
        # Keep the Bearer token out of curl's command-line arguments.
        printf 'header = "Authorization: Bearer %s"\n' "$token" > "$temp_dir/bearer.conf"
        options+=(--config "$(native_path "$temp_dir/bearer.conf")")
    fi
    if [[ "$spoof" == yes ]]; then
        options+=(--header 'X-Authenticated-User: demo-admin'
            --header 'X-Authenticated-Roles: ADMIN' --header 'X-Role: ADMIN')
    fi
    if ! http_status=$(curl "${options[@]}"); then
        fail "$step: HTTP transport failed (connection or timeout)"
    fi
}
expect_status() {
    local expected="$1"
    [[ "$http_status" == "$expected" ]] || fail "$step: expected=$expected actual=$http_status"
    pass "$step: expected=$expected actual=$http_status"
}
expect_gateway() {
    if ! json_tool enforcer "$(native_path "$temp_dir/response.headers")"; then
        fail "$step: response was not identified as a gateway rejection"
    fi
}

step='GATEWAY_HEALTH'
request GET /actuator/health
expect_status 200
step='USER_LOGIN'
if [[ -z "$password" ]]; then
    [[ -t 0 ]] || fail 'USER_LOGIN: set AUTH_TEST_PASSWORD for non-interactive execution'
    IFS= read -r -s -p "Password for $AUTH_TEST_USERNAME: " password || fail 'USER_LOGIN: cancelled'
    printf '\n' >&2
fi
[[ -n "$password" ]] || fail 'USER_LOGIN: empty password'
printf '%s' "$password" | json_tool login "$AUTH_TEST_USERNAME" > "$temp_dir/login.json"
password=''
request POST /api/auth/login "$temp_dir/login.json"
expect_status 200
token=$(json_tool token "$(native_path "$temp_dir/response.json")" "$AUTH_TEST_USERNAME") || fail 'USER_LOGIN: invalid USER token'
rm -f -- "$temp_dir/login.json"
mode="${CONFIG_MODE:-read}"

[[ "$mode" == read || "$mode" == dynamic ]] ||
    fail 'CONFIG_MODE must be read or dynamic'

if [[ "$mode" == dynamic ]]; then
    step='DYNAMIC_BEFORE'
    request GET '/api/textbooks/stock-warning?stock=5' '' yes
    expect_status 200

    json_tool dynamic-before \
        "$(native_path "$temp_dir/response.json")" \
        "${CONFIG_PROFILE:-dev}" ||
        fail 'Baseline must be threshold=3, revision=v1'

    cp "$temp_dir/response.json" "$temp_dir/baseline.json"

    pass 'BASELINE: stock=5 threshold=3 warning=false revision=v1'

    printf '[INFO] Publish threshold=10 and revision=v2 in Nacos now; do not restart/rebuild services.\n'

    for attempt in {1..90}; do
        step='DYNAMIC_AFTER'
        request GET '/api/textbooks/stock-warning?stock=5' '' yes

        [[ "$http_status" == 200 ]] ||
            fail "DYNAMIC_AFTER: expected=200 actual=$http_status"

        if json_tool dynamic-ready \
            "$(native_path "$temp_dir/response.json")" \
            >/dev/null 2>&1; then

            json_tool dynamic-after \
                "$(native_path "$temp_dir/response.json")" \
                "${CONFIG_PROFILE:-dev}" \
                "$(native_path "$temp_dir/baseline.json")" ||
                fail 'Dynamic values, decision or JVM identity did not match'

            pass 'DYNAMIC_CHANGED: threshold=3->10 warning=false->true'
            pass 'SAME_JVM: instance, pid and startedAt unchanged'
            pass 'ALL DYNAMIC CONFIG CHECKS PASSED'
            exit 0
        fi

        sleep 2
    done

    fail 'Dynamic update not observed within the polling window'
fi
profile="${CONFIG_PROFILE:-dev}"
step='USER_CONFIG'
request GET /api/users/config-proof '' yes
expect_status 200
json_tool proof "$(native_path "$temp_dir/response.json")" user-service "${EXPECTED_USER_POOL:-7}" "${EXPECTED_USER_MARKER:-nacos-user-dev-v1}" "$profile" || fail 'USER_CONFIG: configured/actual values did not match expected Nacos values'
pass 'USER_CONFIG: expected remote values match actual Hikari pool'
step='TEXTBOOK_CONFIG'
request GET /api/textbooks/config-proof '' yes
expect_status 200
json_tool proof "$(native_path "$temp_dir/response.json")" textbook-service "${EXPECTED_TEXTBOOK_POOL:-8}" "${EXPECTED_TEXTBOOK_MARKER:-nacos-textbook-dev-v1}" "$profile" || fail 'TEXTBOOK_CONFIG: configured/actual values did not match expected Nacos values'
pass 'TEXTBOOK_CONFIG: expected remote values match actual Hikari pool'
pass 'ALL CONFIG READ CHECKS PASSED'
exit 0
