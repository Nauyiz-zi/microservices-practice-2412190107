#!/usr/bin/env bash
# Week 09: exercise gateway authentication and authorization through real HTTP.
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
            "$temp_dir/response.headers" "$temp_dir/bearer.conf"
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
temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/week09-auth-test.XXXXXXXX")
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

# 1. Without login, protected access must fail.
step='ANONYMOUS_PROTECTED'
request GET /api/auth/me
expect_status 401
expect_gateway
step='FAKE_IDENTITY_WITHOUT_TOKEN'
request GET /api/auth/me '' no yes
expect_status 401
expect_gateway

# 2. Login and obtain a USER token. Read secrets without echoing them.
step='USER_LOGIN'
if [[ -z "$password" ]]; then
    [[ -t 0 ]] || fail 'USER_LOGIN: set AUTH_TEST_PASSWORD for non-interactive execution'
    if ! IFS= read -r -s -p "Password for $AUTH_TEST_USERNAME: " password; then
        printf '\n' >&2
        fail 'USER_LOGIN: password input cancelled'
    fi
    printf '\n' >&2
fi
[[ -n "$password" ]] || fail 'USER_LOGIN: password must not be empty'
printf '%s' "$password" | json_tool login "$AUTH_TEST_USERNAME" > "$temp_dir/login.json"
password=''
request POST /api/auth/login "$temp_dir/login.json"
expect_status 200
if ! token=$(json_tool token "$(native_path "$temp_dir/response.json")" "$AUTH_TEST_USERNAME"); then
    fail 'USER_LOGIN: invalid response or account is not a low-privilege USER'
fi
rm -f -- "$temp_dir/login.json"
pass 'USER_TOKEN: Bearer JWT, USER role, positive TTL (token hidden)'

# 3. A valid token must reach the protected endpoint and return the real identity.
step='AUTHORIZED_PROTECTED'
request GET /api/auth/me '' yes
expect_status 200
if ! json_tool me "$(native_path "$temp_dir/response.json")" "$AUTH_TEST_USERNAME"; then
    fail 'AUTHORIZED_PROTECTED: verified identity did not match the login account'
fi
pass 'VERIFIED_IDENTITY: login account and USER role match'
step='AUTHORIZED_BUSINESS'
request GET /api/textbooks '' yes
expect_status 200

# 4. A low-privilege token must not access the ADMIN endpoint.
step='USER_ADMIN_FORBIDDEN'
request GET /api/auth/accounts '' yes
expect_status 403
expect_gateway
step='FAKE_ADMIN_WITH_USER_TOKEN'
request GET /api/auth/accounts '' yes yes
expect_status 403
expect_gateway

# Additional negative cases: a modified signature and an incorrect password.
step='TAMPERED_TOKEN'
token=$(printf '%s' "$token" | json_tool tamper)
request GET /api/auth/me '' yes
expect_status 401
expect_gateway
token=''
step='WRONG_PASSWORD'
json_tool wrong-login "$AUTH_TEST_USERNAME" > "$temp_dir/wrong-login.json"
request POST /api/auth/login "$temp_dir/wrong-login.json"
expect_status 401

pass 'ALL AUTH TESTS PASSED'
exit 0
