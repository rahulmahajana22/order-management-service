#!/usr/bin/env bash
# =============================================================================
#  overview.sh — Order Management System Interactive Demo
#
#  Usage  : bash overview.sh
#  Shell  : Git Bash (Windows) or any POSIX-compatible bash
#  Needs  : curl (required), jq (optional — pretty JSON)
#
#  Required env vars (same ones the Spring Boot app needs):
#    JWT_SECRET            SEED_ADMIN_USERNAME    SEED_ADMIN_PASSWORD
#    SEED_USER_USERNAME    SEED_USER_PASSWORD
#    H2_DB_USERNAME        H2_DB_PASSWORD
#
#  Demo user note:
#    alice_demo / bob_demo / carol_demo are registered against the seed
#    customer emails (alice@example.com, bob@example.com, carol@example.com).
#    Because AuthController skips customer creation when the email already
#    exists, these users get linked to seed customer IDs 1, 2, 3 — allowing
#    order placement without a separate customer-lookup endpoint.
# =============================================================================
# ─── Load environment variables from .env ────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    set -a; source "$ENV_FILE"; set +a
else
    printf "ERROR: .env file not found at %s\n" "$ENV_FILE" >&2
    printf "       Create it with: JWT_SECRET, SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD,\n" >&2
    printf "       SEED_USER_USERNAME, SEED_USER_PASSWORD, H2_DB_USERNAME, H2_DB_PASSWORD\n" >&2
    exit 1
fi

# ─── ANSI colours ─────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BLUE='\033[0;34m'
BOLD='\033[1m'
DIM='\033[2m'
RESET='\033[0m'

# ─── Application configuration ────────────────────────────────────────────────
BASE_URL="http://localhost:8080"
DEMO_PASSWORD="Demo@1234"        # password used for all registered demo users

# Demo user credentials
ALICE_USER="alice_demo";   ALICE_EMAIL="alice@example.com"  # → seed customer ID 1
BOB_USER="bob_demo";       BOB_EMAIL="bob@example.com"      # → seed customer ID 2
CAROL_USER="carol_demo";   CAROL_EMAIL="carol@example.com"  # → seed customer ID 3
DAVE_USER="dave_demo";     DAVE_EMAIL="dave@demo.com"       # → new customer

# Seed customer IDs (data.sql insertion order)
CUST_ALICE=1
CUST_BOB=2
CUST_CAROL=3

# ─── Global state (shared across layers) ──────────────────────────────────────
TOKEN_ADMIN=""
TOKEN_ALICE=""
TOKEN_BOB=""
TOKEN_CAROL=""
ORDER_ID_ALICE=""
ORDER_ID_BOB=""
ORDER_ID_CAROL=""
AUDIT_ORDER_ID=""
LAST_TOKEN=""   # set by do_login / do_register_or_login
RESP=""         # set by do_request
HTTP_CODE=""    # set by do_request

# ─── Rate-limit tracking ──────────────────────────────────────────────────────
AUTH_CALLS=0;  AUTH_WIN_START=0
ORDER_CALLS=0; ORDER_WIN_START=0

init_rate_limits() {
    AUTH_CALLS=0;  AUTH_WIN_START=$(date +%s)
    ORDER_CALLS=0; ORDER_WIN_START=$(date +%s)
}

# Auth limit: 5/30s — silent gate used by all layers except Layer 5
auth_gate() {
    local now elapsed
    now=$(date +%s)
    elapsed=$(( now - AUTH_WIN_START ))
    if [ $elapsed -ge 30 ]; then
        AUTH_CALLS=0; AUTH_WIN_START=$now; elapsed=0
    fi
    if [ $AUTH_CALLS -ge 5 ]; then
        local wait=$(( 31 - elapsed ))
        [ $wait -lt 1 ] && wait=1
        sleep $wait   # silent — rate limiting is only showcased in Layer 5
        AUTH_CALLS=0; AUTH_WIN_START=$(date +%s)
    fi
    AUTH_CALLS=$(( AUTH_CALLS + 1 ))
}

# Order limit: 6/30s — silent gate used by all layers except Layer 5
order_gate() {
    local now elapsed
    now=$(date +%s)
    elapsed=$(( now - ORDER_WIN_START ))
    if [ $elapsed -ge 30 ]; then
        ORDER_CALLS=0; ORDER_WIN_START=$now; elapsed=0
    fi
    if [ $ORDER_CALLS -ge 6 ]; then
        local wait=$(( 31 - elapsed ))
        [ $wait -lt 1 ] && wait=1
        sleep $wait   # silent — rate limiting is only showcased in Layer 5
        ORDER_CALLS=0; ORDER_WIN_START=$(date +%s)
    fi
    ORDER_CALLS=$(( ORDER_CALLS + 1 ))
}

# ─── Print helpers ─────────────────────────────────────────────────────────────
sep()           { printf "${DIM}  %s${RESET}\n" "──────────────────────────────────────────────────────"; }
print_header()  { printf "\n${BOLD}${CYAN}══  %s${RESET}\n" "$1"; sep; }
print_step()    { printf "\n${BOLD}${BLUE}  ▸ %s${RESET}\n" "$1"; }
print_success() { printf "  ${GREEN}✔  %s${RESET}\n" "$1"; }
print_error()   { printf "  ${RED}✘  %s${RESET}\n" "$1"; }
print_info()    { printf "  ${YELLOW}ℹ  %s${RESET}\n" "$1"; }
print_warn()    { printf "  ${MAGENTA}⚠  %s${RESET}\n" "$1"; }
pause()         { printf "\n${DIM}  Press [Enter] to continue...${RESET}"; read -r _PAUSE; }

# ─── JSON helpers ──────────────────────────────────────────────────────────────
HAS_JQ=false
command -v jq &>/dev/null && HAS_JQ=true

pretty_json() {
    if $HAS_JQ; then
        printf "%s" "$1" | jq . 2>/dev/null || printf "%s\n" "$1"
    else
        printf "%s\n" "$1"
    fi
}

extract_field() {
    local json="$1" field="$2"
    if $HAS_JQ; then
        printf "%s" "$json" | jq -r ".${field} // empty" 2>/dev/null
    else
        printf "%s" "$json" | grep -o "\"${field}\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
    fi
}

extract_numeric_field() {
    local json="$1" field="$2"
    if $HAS_JQ; then
        printf "%s" "$json" | jq -r ".${field} // empty" 2>/dev/null
    else
        printf "%s" "$json" | grep -o "\"${field}\":[0-9]*" | head -1 | grep -o '[0-9]*$'
    fi
}

extract_first_content_id() {
    local json="$1"
    if $HAS_JQ; then
        printf "%s" "$json" | jq -r '.content[0].id // empty' 2>/dev/null
    else
        printf "%s" "$json" | grep -o '"content":\[{"id":[0-9]*' | grep -o '[0-9]*$'
    fi
}

# ─── JWT payload decoder ──────────────────────────────────────────────────────
decode_jwt() {
    local token="$1"
    local payload
    payload=$(printf "%s" "$token" | cut -d. -f2)
    # Add base64 padding
    local mod=$(( ${#payload} % 4 ))
    case $mod in
        2) payload="${payload}==" ;;
        3) payload="${payload}=" ;;
    esac
    # Swap URL-safe chars to standard base64
    payload=$(printf "%s" "$payload" | tr '_-' '/+')
    printf "%s" "$payload" | base64 -d 2>/dev/null || printf "[base64 decode unavailable]\n"
}

# ─── HTTP request wrapper ─────────────────────────────────────────────────────
# Sets globals: RESP, HTTP_CODE
do_request() {
    local method="$1" url="$2" token="${3:-}" body="${4:-}"
    local args=(-s -X "$method" "$url" -H "Content-Type: application/json")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
    [ -n "$body"  ] && args+=(--data "$body")
    local raw
    raw=$(curl "${args[@]}" -w "|||%{http_code}")
    HTTP_CODE="${raw##*|||}"
    RESP="${raw%|||*}"
}

print_response() {
    printf "  HTTP ${BOLD}%s${RESET}\n" "$HTTP_CODE"
    pretty_json "$RESP"
}

# ─── Auth wrappers (set LAST_TOKEN) ───────────────────────────────────────────
do_login() {
    local username="$1" password="$2"
    auth_gate
    local body; body=$(printf '{"username":"%s","password":"%s"}' "$username" "$password")
    do_request POST "$BASE_URL/api/auth/login" "" "$body"
    print_response
    LAST_TOKEN=$(extract_field "$RESP" "token")
}

# Try register; fall back to login on conflict — makes the script idempotent
do_register_or_login() {
    local username="$1" password="$2" email="$3"
    auth_gate
    local body; body=$(printf '{"username":"%s","password":"%s","email":"%s"}' "$username" "$password" "$email")
    do_request POST "$BASE_URL/api/auth/register" "" "$body"
    if [ "$HTTP_CODE" = "201" ]; then
        print_success "Registered $username (HTTP 201)"
        print_response
        LAST_TOKEN=$(extract_field "$RESP" "token")
    else
        print_warn "$username already exists (HTTP $HTTP_CODE) — logging in instead"
        auth_gate
        local login_body; login_body=$(printf '{"username":"%s","password":"%s"}' "$username" "$password")
        do_request POST "$BASE_URL/api/auth/login" "" "$login_body"
        if [ "$HTTP_CODE" = "200" ]; then
            print_success "Logged in as $username (HTTP 200)"
        else
            print_error "Login also failed (HTTP $HTTP_CODE)"
            print_response
        fi
        LAST_TOKEN=$(extract_field "$RESP" "token")
    fi
}

# ─── Prerequisites check ──────────────────────────────────────────────────────
check_prereqs() {
    clear
    printf "${BOLD}${CYAN}\n  Order Management System — Interactive API Demo${RESET}\n"
    printf "${DIM}  Base URL : %s${RESET}\n\n" "$BASE_URL"
    print_header "Checking Prerequisites"

    if ! command -v curl &>/dev/null; then
        print_error "curl is required but not found. Please install it and re-run."
        exit 1
    fi
    print_success "curl: $(curl --version | head -1)"

    if $HAS_JQ; then
        print_success "jq $(jq --version) — JSON output will be pretty-printed"
    else
        print_warn "jq not found — install it for pretty JSON output (continuing anyway)"
    fi

    local missing=0
    for var in JWT_SECRET SEED_ADMIN_USERNAME SEED_ADMIN_PASSWORD \
               SEED_USER_USERNAME SEED_USER_PASSWORD H2_DB_USERNAME H2_DB_PASSWORD; do
        if [ -z "${!var:-}" ]; then
            print_error "Missing env var: $var"
            missing=$(( missing + 1 ))
        else
            print_success "Env var set: $var"
        fi
    done

    if [ $missing -gt 0 ]; then
        printf "\n${RED}  $missing required env var(s) unset. Export them and re-run.${RESET}\n\n"
        exit 1
    fi

    print_step "API health check (GET /actuator/health)"
    do_request GET "$BASE_URL/actuator/health"
    if [ "$HTTP_CODE" = "200" ]; then
        print_success "API is UP"
        print_response
    else
        print_error "API not reachable (HTTP $HTTP_CODE). Is the server running on $BASE_URL?"
        exit 1
    fi
}

# =============================================================================
# LAYER 1 — User Registration & Login
# =============================================================================
layer_1() {
    print_header "LAYER 1 — User Registration & Login"
    init_rate_limits

    print_info "Auth endpoint: POST /api/auth/register  |  POST /api/auth/login"
    print_info "Rate limit: 5 requests per 30 seconds (demonstrated in Layer 5)"
    printf "\n"

    # ── Register 4 demo users ─────────────────────────────────────────────────
    print_step "Registering demo users"

    printf "\n${BOLD}  [1/4] %s (%s)${RESET}\n" "$ALICE_USER" "$ALICE_EMAIL"
    do_register_or_login "$ALICE_USER" "$DEMO_PASSWORD" "$ALICE_EMAIL"
    TOKEN_ALICE="$LAST_TOKEN"

    printf "\n${BOLD}  [2/4] %s (%s)${RESET}\n" "$BOB_USER" "$BOB_EMAIL"
    do_register_or_login "$BOB_USER" "$DEMO_PASSWORD" "$BOB_EMAIL"
    TOKEN_BOB="$LAST_TOKEN"

    printf "\n${BOLD}  [3/4] %s (%s)${RESET}\n" "$CAROL_USER" "$CAROL_EMAIL"
    do_register_or_login "$CAROL_USER" "$DEMO_PASSWORD" "$CAROL_EMAIL"
    TOKEN_CAROL="$LAST_TOKEN"

    printf "\n${BOLD}  [4/4] %s (%s)${RESET}\n" "$DAVE_USER" "$DAVE_EMAIL"
    do_register_or_login "$DAVE_USER" "$DEMO_PASSWORD" "$DAVE_EMAIL"
    print_info "dave_demo registered (used for auth demo only)"

    # ── Negative: duplicate registration ──────────────────────────────────────
    print_step "Negative scenario — duplicate registration (expect HTTP 400)"
    auth_gate
    local dup_body; dup_body=$(printf '{"username":"%s","password":"%s","email":"%s"}' \
        "$ALICE_USER" "$DEMO_PASSWORD" "$ALICE_EMAIL")
    do_request POST "$BASE_URL/api/auth/register" "" "$dup_body"
    print_response
    if [ "$HTTP_CODE" != "201" ]; then
        print_success "Duplicate correctly rejected (HTTP $HTTP_CODE)"
    fi

    # ── Login as admin ────────────────────────────────────────────────────────
    print_step "Login as admin (POST /api/auth/login)"
    printf "  Admin username: ${BOLD}%s${RESET}\n" "$SEED_ADMIN_USERNAME"
    do_login "$SEED_ADMIN_USERNAME" "$SEED_ADMIN_PASSWORD"
    TOKEN_ADMIN="$LAST_TOKEN"
    if [ -n "$TOKEN_ADMIN" ]; then
        print_success "Admin token acquired"
    else
        print_error "Admin login failed — check SEED_ADMIN_USERNAME / SEED_ADMIN_PASSWORD"
    fi

    # ── Negative: wrong password ──────────────────────────────────────────────
    print_step "Negative scenario — wrong password (expect HTTP 401)"
    auth_gate
    local bad_body; bad_body=$(printf '{"username":"%s","password":"WrongPass!"}' "$ALICE_USER")
    do_request POST "$BASE_URL/api/auth/login" "" "$bad_body"
    print_response
    [ "$HTTP_CODE" = "401" ] && print_success "Bad credentials correctly rejected (HTTP 401)"

    print_header "Layer 1 Complete"
    pause
}

# =============================================================================
# LAYER 2 — JWT Tokens & RBAC Roles
# =============================================================================
layer_2() {
    print_header "LAYER 2 — JWT Tokens & RBAC Roles"
    init_rate_limits

    # Ensure tokens
    if [ -z "$TOKEN_ALICE" ]; then
        print_info "Acquiring alice_demo token..."
        do_login "$ALICE_USER" "$DEMO_PASSWORD"; TOKEN_ALICE="$LAST_TOKEN"
    fi
    if [ -z "$TOKEN_ADMIN" ]; then
        print_info "Acquiring admin token..."
        do_login "$SEED_ADMIN_USERNAME" "$SEED_ADMIN_PASSWORD"; TOKEN_ADMIN="$LAST_TOKEN"
    fi

    # ── Display raw tokens ────────────────────────────────────────────────────
    print_step "USER token (alice_demo):"
    printf "  ${CYAN}%s${RESET}\n" "$TOKEN_ALICE"

    print_step "ADMIN token ($SEED_ADMIN_USERNAME):"
    printf "  ${MAGENTA}%s${RESET}\n" "$TOKEN_ADMIN"

    # ── Decode JWT payloads ───────────────────────────────────────────────────
    print_step "Decoding JWT payloads (base64url decode of the middle segment)"

    printf "\n  ${BOLD}USER payload:${RESET}\n"
    local user_payload; user_payload=$(decode_jwt "$TOKEN_ALICE")
    pretty_json "$user_payload"

    printf "\n  ${BOLD}ADMIN payload:${RESET}\n"
    local admin_payload; admin_payload=$(decode_jwt "$TOKEN_ADMIN")
    pretty_json "$admin_payload"

    print_info "Both tokens contain the same claims (sub, iat, exp)."
    print_info "The ROLE is NOT embedded in the JWT — it is looked up from the"
    print_info "database on every request via CustomUserDetailsService."
    print_info "This means role changes take effect immediately without re-issuing a token."

    # ── Same endpoint, different RBAC scope ───────────────────────────────────
    print_step "GET /api/orders with USER token → sees only own orders"
    order_gate
    do_request GET "$BASE_URL/api/orders" "$TOKEN_ALICE"
    print_response

    print_step "GET /api/orders with ADMIN token → sees ALL orders"
    order_gate
    do_request GET "$BASE_URL/api/orders" "$TOKEN_ADMIN"
    print_response

    print_info "Same endpoint, different scope. RBAC enforced at the service layer via SecurityService."

    print_header "Layer 2 Complete"
    pause
}

# =============================================================================
# LAYER 3 — View Orders (pagination, filtering)
# =============================================================================
layer_3() {
    print_header "LAYER 3 — View Orders"
    init_rate_limits

    if [ -z "$TOKEN_ALICE" ]; then
        do_login "$ALICE_USER" "$DEMO_PASSWORD"; TOKEN_ALICE="$LAST_TOKEN"
    fi
    if [ -z "$TOKEN_ADMIN" ]; then
        do_login "$SEED_ADMIN_USERNAME" "$SEED_ADMIN_PASSWORD"; TOKEN_ADMIN="$LAST_TOKEN"
    fi

    # ── User sees only own orders ─────────────────────────────────────────────
    print_step "alice_demo — GET /api/orders (own orders only)"
    order_gate
    do_request GET "$BASE_URL/api/orders" "$TOKEN_ALICE"
    print_response
    print_info "alice only sees her own orders — RBAC filters by user email."

    # ── Admin sees all orders ─────────────────────────────────────────────────
    print_step "Admin — GET /api/orders (all orders, default page)"
    order_gate
    do_request GET "$BASE_URL/api/orders" "$TOKEN_ADMIN"
    print_response

    # ── Pagination ────────────────────────────────────────────────────────────
    print_step "Admin — GET /api/orders?page=0&size=2&sort=createdAt,desc"
    order_gate
    do_request GET "$BASE_URL/api/orders?page=0&size=2&sort=createdAt,desc" "$TOKEN_ADMIN"
    print_response
    print_info "Spring Page<T> response: content[], totalElements, totalPages, number."

    # ── Filter by status ──────────────────────────────────────────────────────
    print_step "Admin — GET /api/orders?status=PENDING (filter by status)"
    order_gate
    do_request GET "$BASE_URL/api/orders?status=PENDING" "$TOKEN_ADMIN"
    print_response

    print_header "Layer 3 Complete"
    pause
}

# =============================================================================
# LAYER 4 — Place Orders & RBAC Scenarios
# =============================================================================
layer_4() {
    print_header "LAYER 4 — Place Orders & RBAC Scenarios"
    init_rate_limits

    # Fresh logins for all actors
    print_step "Acquiring tokens for alice, bob, carol, and admin..."
    do_login "$ALICE_USER" "$DEMO_PASSWORD";          TOKEN_ALICE="$LAST_TOKEN"
    do_login "$BOB_USER"   "$DEMO_PASSWORD";          TOKEN_BOB="$LAST_TOKEN"
    do_login "$CAROL_USER" "$DEMO_PASSWORD";          TOKEN_CAROL="$LAST_TOKEN"
    do_login "$SEED_ADMIN_USERNAME" "$SEED_ADMIN_PASSWORD"; TOKEN_ADMIN="$LAST_TOKEN"
    print_success "All tokens acquired"

    # ── POSITIVE: each user places their own order ────────────────────────────
    print_step "Positive — alice_demo places an order (customer ID $CUST_ALICE)"
    print_info "Items: Laptop Stand x1, USB Hub x2"
    order_gate
    local body_alice; body_alice=$(printf \
        '{"customerId":%d,"items":[{"productId":1,"quantity":1},{"productId":2,"quantity":2}]}' \
        "$CUST_ALICE")
    do_request POST "$BASE_URL/api/orders" "$TOKEN_ALICE" "$body_alice"
    print_response
    ORDER_ID_ALICE=$(extract_numeric_field "$RESP" "id")
    [ -n "$ORDER_ID_ALICE" ] && print_success "Alice's order created — ID: $ORDER_ID_ALICE"

    print_step "Positive — bob_demo places an order (customer ID $CUST_BOB)"
    print_info "Items: Wireless Mouse x1, Mechanical Keyboard x1"
    order_gate
    local body_bob; body_bob=$(printf \
        '{"customerId":%d,"items":[{"productId":3,"quantity":1},{"productId":4,"quantity":1}]}' \
        "$CUST_BOB")
    do_request POST "$BASE_URL/api/orders" "$TOKEN_BOB" "$body_bob"
    print_response
    ORDER_ID_BOB=$(extract_numeric_field "$RESP" "id")
    [ -n "$ORDER_ID_BOB" ] && print_success "Bob's order created — ID: $ORDER_ID_BOB"

    print_step "Positive — carol_demo places an order (customer ID $CUST_CAROL)"
    print_info "Items: Monitor Light x2, Laptop Stand x1"
    order_gate
    local body_carol; body_carol=$(printf \
        '{"customerId":%d,"items":[{"productId":5,"quantity":2},{"productId":1,"quantity":1}]}' \
        "$CUST_CAROL")
    do_request POST "$BASE_URL/api/orders" "$TOKEN_CAROL" "$body_carol"
    print_response
    ORDER_ID_CAROL=$(extract_numeric_field "$RESP" "id")
    [ -n "$ORDER_ID_CAROL" ] && print_success "Carol's order created — ID: $ORDER_ID_CAROL"

    # ── RBAC: user sees only own orders ──────────────────────────────────────
    print_step "alice_demo — GET /api/orders (should show only HER order)"
    order_gate
    do_request GET "$BASE_URL/api/orders" "$TOKEN_ALICE"
    print_response
    print_success "RBAC scoping confirmed — alice sees only her own orders"

    # ── NEGATIVE: cross-user GET ──────────────────────────────────────────────
    if [ -n "$ORDER_ID_BOB" ]; then
        print_step "Negative — alice tries to GET bob's order (ID $ORDER_ID_BOB, expect 403/404)"
        order_gate
        do_request GET "$BASE_URL/api/orders/$ORDER_ID_BOB" "$TOKEN_ALICE"
        print_response
        ( [ "$HTTP_CODE" = "403" ] || [ "$HTTP_CODE" = "404" ] ) && \
            print_success "Cross-user read correctly denied (HTTP $HTTP_CODE)"
    fi

    # ── NEGATIVE: USER tries PATCH (ADMIN only) ───────────────────────────────
    if [ -n "$ORDER_ID_ALICE" ]; then
        print_step "Negative — alice tries PATCH /api/orders/$ORDER_ID_ALICE/status (expect 403)"
        order_gate
        do_request PATCH "$BASE_URL/api/orders/$ORDER_ID_ALICE/status" \
            "$TOKEN_ALICE" '{"status":"PROCESSING"}'
        print_response
        [ "$HTTP_CODE" = "403" ] && print_success "Status update forbidden for USER (HTTP 403)"
    fi

    # ── NEGATIVE: USER tries DELETE (ADMIN only) ──────────────────────────────
    if [ -n "$ORDER_ID_ALICE" ]; then
        print_step "Negative — alice tries DELETE /api/orders/$ORDER_ID_ALICE (expect 403)"
        order_gate
        do_request DELETE "$BASE_URL/api/orders/$ORDER_ID_ALICE" "$TOKEN_ALICE"
        print_response
        [ "$HTTP_CODE" = "403" ] && print_success "Order cancellation forbidden for USER (HTTP 403)"
    fi

    # ── NEGATIVE: unauthenticated ─────────────────────────────────────────────
    print_step "Negative — unauthenticated GET /api/orders (no token, expect 401)"
    order_gate
    do_request GET "$BASE_URL/api/orders" ""
    print_response
    [ "$HTTP_CODE" = "401" ] && print_success "Unauthenticated request correctly rejected (HTTP 401)"

    # ── ADMIN: sees all orders ────────────────────────────────────────────────
    print_step "Admin — GET /api/orders (sees orders from all three users)"
    order_gate
    do_request GET "$BASE_URL/api/orders" "$TOKEN_ADMIN"
    print_response
    print_success "Admin has full visibility across all user orders"

    # ── ADMIN: update order status ────────────────────────────────────────────
    if [ -n "$ORDER_ID_BOB" ]; then
        print_step "Admin — PATCH /api/orders/$ORDER_ID_BOB/status → PROCESSING"
        order_gate
        do_request PATCH "$BASE_URL/api/orders/$ORDER_ID_BOB/status" \
            "$TOKEN_ADMIN" '{"status":"PROCESSING"}'
        print_response
        [ "$HTTP_CODE" = "200" ] && print_success "Bob's order moved to PROCESSING"
    fi

    # ── ADMIN: cancel a PENDING order ─────────────────────────────────────────
    if [ -n "$ORDER_ID_CAROL" ]; then
        print_step "Admin — DELETE /api/orders/$ORDER_ID_CAROL (cancel carol's PENDING order)"
        order_gate
        do_request DELETE "$BASE_URL/api/orders/$ORDER_ID_CAROL" "$TOKEN_ADMIN"
        print_response
        ( [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "204" ] ) && \
            print_success "Carol's order cancelled by admin"
    fi

    # Persist alice's order for Layer 6
    AUDIT_ORDER_ID="$ORDER_ID_ALICE"

    print_header "Layer 4 Complete"
    pause
}

# =============================================================================
# LAYER 5 — Rate Limiting
# =============================================================================
layer_5() {
    print_header "LAYER 5 — Rate Limiting (Resilience4j)"

    print_info "Auth  endpoints : 5 requests / 30 seconds"
    print_info "Order endpoints : 6 requests / 30 seconds"
    print_info "Response on breach: HTTP 429 — 'Too many requests — please slow down'"
    printf "\n"

    # ── Auth burst ────────────────────────────────────────────────────────────
    print_step "Firing 6 rapid POST /api/auth/login calls (limit is 5/30s)"
    printf "  ${DIM}Expect: calls 1–5 → 200, call 6 → 429${RESET}\n\n"

    local body; body=$(printf '{"username":"%s","password":"%s"}' "$ALICE_USER" "$DEMO_PASSWORD")
    local captured_token=""

    for i in 1 2 3 4 5 6; do
        printf "  ${BOLD}Auth request #%d:${RESET}\n" $i
        # Intentionally NO auth_gate — we want to hit the limit
        do_request POST "$BASE_URL/api/auth/login" "" "$body"
        printf "  HTTP ${BOLD}%s${RESET} → " "$HTTP_CODE"
        case "$HTTP_CODE" in
            200)  printf "${GREEN}OK${RESET}\n";
                  [ -z "$captured_token" ] && captured_token=$(extract_field "$RESP" "token") ;;
            429)  printf "${RED}429 Too Many Requests${RESET}\n"; pretty_json "$RESP";
                  print_success "Rate limit triggered as expected!" ;;
            *)    printf "${YELLOW}%s${RESET}\n" "$HTTP_CODE"; pretty_json "$RESP" ;;
        esac
        sleep 1
    done

    # Use the captured token or fall back to existing
    [ -n "$captured_token" ] && TOKEN_ALICE="$captured_token"

    printf "\n  ${YELLOW}Waiting 31s for auth rate-limit window to reset...${RESET}\n"
    sleep 31

    # ── Reload token after reset ──────────────────────────────────────────────
    if [ -z "$TOKEN_ALICE" ]; then
        print_info "Acquiring alice token for order burst..."
        do_request POST "$BASE_URL/api/auth/login" "" "$body"
        TOKEN_ALICE=$(extract_field "$RESP" "token")
        printf "\n  ${YELLOW}Waiting 31s to reset auth window used for token refresh...${RESET}\n"
        sleep 31
    fi

    # ── Order burst ───────────────────────────────────────────────────────────
    print_step "Firing 7 rapid GET /api/orders calls (limit is 6/30s)"
    printf "  ${DIM}Expect: calls 1–6 → 200, call 7 → 429${RESET}\n\n"

    for i in 1 2 3 4 5 6 7; do
        printf "  ${BOLD}Order request #%d:${RESET}\n" $i
        # Intentionally NO order_gate
        do_request GET "$BASE_URL/api/orders" "$TOKEN_ALICE"
        printf "  HTTP ${BOLD}%s${RESET} → " "$HTTP_CODE"
        case "$HTTP_CODE" in
            200)  printf "${GREEN}OK${RESET}\n" ;;
            429)  printf "${RED}429 Too Many Requests${RESET}\n"; pretty_json "$RESP";
                  print_success "Rate limit triggered as expected!" ;;
            *)    printf "${YELLOW}%s${RESET}\n" "$HTTP_CODE" ;;
        esac
        sleep 1
    done

    printf "\n  ${YELLOW}Waiting 31s for order rate-limit window to reset...${RESET}\n"
    sleep 31
    init_rate_limits   # fresh baseline for any subsequent layers

    print_header "Layer 5 Complete"
    pause
}

# =============================================================================
# LAYER 6 — Audit Trail & Order Lifecycle
# =============================================================================
layer_6() {
    print_header "LAYER 6 — Audit Trail & Order Lifecycle"
    init_rate_limits

    if [ -z "$TOKEN_ADMIN" ]; then
        do_login "$SEED_ADMIN_USERNAME" "$SEED_ADMIN_PASSWORD"; TOKEN_ADMIN="$LAST_TOKEN"
    fi

    # Create a fresh PENDING order if none exists from Layer 4
    if [ -z "$AUDIT_ORDER_ID" ]; then
        print_step "Creating a fresh PENDING order for the audit demo..."
        if [ -z "$TOKEN_ALICE" ]; then
            do_login "$ALICE_USER" "$DEMO_PASSWORD"; TOKEN_ALICE="$LAST_TOKEN"
        fi
        order_gate
        local body; body=$(printf \
            '{"customerId":%d,"items":[{"productId":1,"quantity":1},{"productId":3,"quantity":1}]}' \
            "$CUST_ALICE")
        do_request POST "$BASE_URL/api/orders" "$TOKEN_ALICE" "$body"
        print_response
        AUDIT_ORDER_ID=$(extract_numeric_field "$RESP" "id")
        [ -n "$AUDIT_ORDER_ID" ] && print_success "Order $AUDIT_ORDER_ID created"
    else
        print_info "Using order ID $AUDIT_ORDER_ID from Layer 4"
    fi

    # ── Walk through full status lifecycle ────────────────────────────────────
    print_step "Admin walks order $AUDIT_ORDER_ID through full lifecycle"

    for status in PROCESSING SHIPPED DELIVERED; do
        printf "\n  ${BOLD}→ Updating to %s${RESET}\n" "$status"
        order_gate
        local s_body; s_body=$(printf '{"status":"%s"}' "$status")
        do_request PATCH "$BASE_URL/api/orders/$AUDIT_ORDER_ID/status" "$TOKEN_ADMIN" "$s_body"
        print_response
        [ "$HTTP_CODE" = "200" ] && print_success "Status updated to $status"
        sleep 1   # ensures audit timestamps are distinct
    done

    # ── View revision history ─────────────────────────────────────────────────
    print_step "GET /api/orders/$AUDIT_ORDER_ID/revisions — Hibernate Envers audit trail"
    order_gate
    do_request GET "$BASE_URL/api/orders/$AUDIT_ORDER_ID/revisions" "$TOKEN_ADMIN"
    print_response
    print_info "Each revision captures: what changed, when, and who made the change."
    print_info "Powered by Hibernate Envers — zero application-layer audit code needed."

    # ── Negative: cancel a non-PENDING order ─────────────────────────────────
    print_step "Negative — admin tries to cancel a DELIVERED order (expect 400 — business rule)"
    order_gate
    do_request DELETE "$BASE_URL/api/orders/$AUDIT_ORDER_ID" "$TOKEN_ADMIN"
    print_response
    [ "$HTTP_CODE" = "400" ] && \
        print_success "Business rule enforced: only PENDING orders can be cancelled (HTTP 400)"

    # ── View single order ─────────────────────────────────────────────────────
    print_step "GET /api/orders/$AUDIT_ORDER_ID — final state"
    order_gate
    do_request GET "$BASE_URL/api/orders/$AUDIT_ORDER_ID" "$TOKEN_ADMIN"
    print_response

    # ── Health check ──────────────────────────────────────────────────────────
    print_step "GET /actuator/health"
    do_request GET "$BASE_URL/actuator/health"
    print_response

    print_header "Layer 6 Complete"
    pause
}

# =============================================================================
# Full demo
# =============================================================================
full_demo() {
    print_header "FULL DEMO — All 6 layers in sequence"
    print_info "Rate-limit pauses are managed automatically."
    print_info "Estimated time with fresh database: ~5–7 minutes (rate-limit waits included)."
    printf "\n"
    layer_1
    layer_2
    layer_3
    layer_4
    layer_5
    layer_6
    printf "\n${BOLD}${GREEN}  ╔═══════════════════════════════════╗${RESET}\n"
    printf "${BOLD}${GREEN}  ║   FULL DEMO COMPLETE — all good!  ║${RESET}\n"
    printf "${BOLD}${GREEN}  ╚═══════════════════════════════════╝${RESET}\n\n"
}

# =============================================================================
# Menu
# =============================================================================
show_menu() {
    printf "\n"
    printf "${BOLD}${CYAN}  ╔══════════════════════════════════════════════════════╗${RESET}\n"
    printf "${BOLD}${CYAN}  ║       Order Management System — Live Demo            ║${RESET}\n"
    printf "${BOLD}${CYAN}  ╠══════════════════════════════════════════════════════╣${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}1.${RESET} Layer 1 — User Registration & Login              ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}2.${RESET} Layer 2 — JWT Tokens & RBAC Roles               ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}3.${RESET} Layer 3 — View Orders (pagination & filtering)   ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}4.${RESET} Layer 4 — Place Orders & RBAC Scenarios         ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}5.${RESET} Layer 5 — Rate Limiting (HTTP 429 demo)         ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}6.${RESET} Layer 6 — Audit Trail & Order Lifecycle         ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ╠══════════════════════════════════════════════════════╣${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}7.${RESET} Run Full Demo (all layers, ~5–7 min)            ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ║${RESET}  ${BOLD}0.${RESET} Exit                                            ${BOLD}${CYAN}║${RESET}\n"
    printf "${BOLD}${CYAN}  ╚══════════════════════════════════════════════════════╝${RESET}\n"
    printf "\n  ${BOLD}Choose an option [0–7]:${RESET} "
}

main() {
    check_prereqs

    while true; do
        show_menu
        read -r choice
        case "$choice" in
            1) layer_1 ;;
            2) layer_2 ;;
            3) layer_3 ;;
            4) layer_4 ;;
            5) layer_5 ;;
            6) layer_6 ;;
            7) full_demo ;;
            0) printf "\n  ${BOLD}Goodbye!${RESET}\n\n"; exit 0 ;;
            *) print_error "Invalid choice. Enter a number from 0 to 7." ;;
        esac
    done
}

main "$@"
