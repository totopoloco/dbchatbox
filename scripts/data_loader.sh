#!/usr/bin/env bash
# =============================================================================
# scripts/data_loader.sh — Phase 2: Multi-tenant + auth data loader
#
# Creates demo data for all 3 tenants via the GraphQL API using JWT authentication.
# Credentials are written to scripts/keycloak-credentials.txt (git-ignored).
#
# USAGE:
#   ./scripts/data_loader.sh              # load data + generate API keys
#   ./scripts/data_loader.sh --verify     # load data + run verification queries
#
# PREREQUISITES:
#   - jq     (apt install jq)
#   - curl
#   - python3
#   - Keycloak running (devcontainer: docker compose up keycloak)
#   - Application running (./gradlew bootRun, dev profile)
#   - Restart the app before re-running (H2 in-memory DB resets on restart)
#
# Override the target URL:
#   BASE_URL=http://localhost:9090/graphql ./scripts/data_loader.sh
# =============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/graphql}"
CREDENTIALS_FILE="${CREDENTIALS_FILE:-$(dirname "$0")/keycloak-credentials.txt}"
VERIFY="${1:-}"

# ── Guards ────────────────────────────────────────────────────────────────────

command -v jq      >/dev/null 2>&1 || { echo "ERROR: jq is required";     exit 1; }
command -v curl    >/dev/null 2>&1 || { echo "ERROR: curl is required";    exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 is required"; exit 1; }

# ── Helpers ───────────────────────────────────────────────────────────────────

# GraphQL call WITHOUT auth (for login/refreshToken mutations only).
gql_noauth() {
  local query="$1"
  local payload
  payload=$(jq -n --arg q "$query" '{"query": $q}')
  curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    -d "$payload"
}

# GraphQL call WITH Bearer auth.
gql_auth() {
  local token="$1"
  local query="$2"
  local payload
  payload=$(jq -n --arg q "$query" '{"query": $q}')
  curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "$payload"
}

# Like gql_auth but extract a jq path; abort on GraphQL errors.
gql_field_auth() {
  local token="$1"
  local jqpath="$2"
  local query="$3"
  local response
  response=$(gql_auth "$token" "$query")
  if echo "$response" | jq -e '.errors | length > 0' >/dev/null 2>&1; then
    echo "ERROR: GraphQL returned errors (path: ${jqpath}):" >&2
    echo "$response" | jq '.errors' >&2
    exit 1
  fi
  echo "$response" | jq -r "$jqpath"
}

# Login via the public login mutation; returns the access token.
get_token() {
  local tenant_slug="$1"
  local username="$2"
  local password="$3"
  local response
  response=$(gql_noauth "mutation {
    login(input: { tenantSlug: \"$tenant_slug\", username: \"$username\", password: \"$password\" }) {
      accessToken
    }
  }")
  if echo "$response" | jq -e '.errors | length > 0' >/dev/null 2>&1; then
    echo "ERROR: login failed for $username @ $tenant_slug:" >&2
    echo "$response" | jq '.errors' >&2
    exit 1
  fi
  echo "$response" | jq -r '.data.login.accessToken'
}

# Decode the `sub` claim from a JWT without verifying the signature.
jwt_sub() {
  local token="$1"
  python3 - "$token" <<'PYEOF'
import sys, base64, json
tok = sys.argv[1]
payload = tok.split('.')[1]
payload += '=' * (-len(payload) % 4)
print(json.loads(base64.urlsafe_b64decode(payload))['sub'])
PYEOF
}

# Call `me` with a user token (JIT-provisions AppUser) and return keycloakSubject.
me_subject() {
  local token="$1"
  gql_field_auth "$token" ".data.me.keycloakSubject" \
    'query { me { keycloakSubject } }'
}

# ── Per-tenant data loader ────────────────────────────────────────────────────

load_tenant_data() {
  local slug="$1"
  local admin_token="$2"
  local label_prefix="$3"

  echo "  Creating trainers" >&2
  local trainer_a
  trainer_a=$(gql_field_auth "$admin_token" ".data.createTrainer.id" "mutation {
    createTrainer(input: {
      firstName: \"${label_prefix}Trainer-A\"
      lastName: \"Alpha\"
      email: \"trainer-a@${slug}.example.com\"
      hourlyRate: 35.00
      paymentMode: \"MONTHLY\"
      autoApproveHours: true
    }) { id }
  }")
  echo "    Trainer A: $trainer_a" >&2

  local trainer_b
  trainer_b=$(gql_field_auth "$admin_token" ".data.createTrainer.id" "mutation {
    createTrainer(input: {
      firstName: \"${label_prefix}Trainer-B\"
      lastName: \"Beta\"
      email: \"trainer-b@${slug}.example.com\"
      hourlyRate: 40.00
      paymentMode: \"PER_SESSION\"
      autoApproveHours: false
    }) { id }
  }")
  echo "    Trainer B: $trainer_b" >&2

  echo "  Creating membership types" >&2
  local mt_basic
  mt_basic=$(gql_field_auth "$admin_token" ".data.createMembershipType.id" "mutation {
    createMembershipType(input: {
      name: \"${label_prefix} Monthly Basic\"
      description: \"Month-to-month access\"
      price: 49.00
      duration: 1
      unit: \"MONTHS\"
      proratedMode: true
      gracePeriodDays: 7
    }) { id }
  }")
  gql_field_auth "$admin_token" ".data.changeMembershipTypeStatus.id" \
    "mutation { changeMembershipTypeStatus(id: \"$mt_basic\", status: \"ACTIVE\") { id } }" >/dev/null
  echo "    MembershipType Basic: $mt_basic" >&2

  echo "  Creating members" >&2
  local member_a
  member_a=$(gql_field_auth "$admin_token" ".data.createMember.id" "mutation {
    createMember(input: {
      firstName: \"${label_prefix}Member\"
      lastName: \"A\"
      email: \"member-a@${slug}.example.com\"
      memberSince: \"2026-01-01\"
    }) { id }
  }")
  echo "    Member A: $member_a" >&2

  local member_b
  member_b=$(gql_field_auth "$admin_token" ".data.createMember.id" "mutation {
    createMember(input: {
      firstName: \"${label_prefix}Member\"
      lastName: \"B\"
      email: \"member-b@${slug}.example.com\"
      memberSince: \"2026-03-01\"
    }) { id }
  }")
  echo "    Member B: $member_b" >&2

  echo "  Creating subscription + payment" >&2
  local sub_a
  sub_a=$(gql_field_auth "$admin_token" ".data.subscribeMember.id" "mutation {
    subscribeMember(input: {
      memberId: \"$member_a\"
      membershipTypeId: \"$mt_basic\"
      startDate: \"2026-01-01\"
      agreedPrice: 49.00
    }) { id }
  }")
  gql_field_auth "$admin_token" ".data.recordPayment.id" \
    "mutation { recordPayment(input: { memberSubscriptionId: \"$sub_a\", amount: 49.00, currency: \"EUR\", paymentDate: \"2026-01-05\", notes: \"January\" }) { id } }" \
    >/dev/null
  echo "    Subscription: $sub_a (paid)" >&2

  # stdout: the IDs needed by the caller
  echo "$trainer_a $trainer_b $member_a $member_b"
}

# ── Link Keycloak seed users to domain entities ───────────────────────────────

link_users() {
  local slug="$1"
  local admin_token="$2"
  local member_username="$3"
  local member_password="$4"
  local trainer_username="$5"
  local trainer_password="$6"
  local domain_member_id="$7"
  local domain_trainer_id="$8"

  echo "  Provisioning AppUser for $member_username"
  local member_token
  member_token=$(get_token "$slug" "$member_username" "$member_password")
  local member_sub
  member_sub=$(me_subject "$member_token")
  echo "    sub: $member_sub"

  echo "  Provisioning AppUser for $trainer_username"
  local trainer_token
  trainer_token=$(get_token "$slug" "$trainer_username" "$trainer_password")
  local trainer_sub
  trainer_sub=$(me_subject "$trainer_token")
  echo "    sub: $trainer_sub"

  echo "  Linking $member_username → member $domain_member_id"
  gql_field_auth "$admin_token" ".data.linkAppUser.id" \
    "mutation { linkAppUser(input: { keycloakSubject: \"$member_sub\", memberId: \"$domain_member_id\" }) { id } }" \
    >/dev/null

  echo "  Linking $trainer_username → trainer $domain_trainer_id"
  gql_field_auth "$admin_token" ".data.linkAppUser.id" \
    "mutation { linkAppUser(input: { keycloakSubject: \"$trainer_sub\", trainerId: \"$domain_trainer_id\" }) { id } }" \
    >/dev/null
}

# ── Credentials file helpers ──────────────────────────────────────────────────

creds_reset() {
  cat > "$CREDENTIALS_FILE" <<EOF
# =============================================================================
# scripts/keycloak-credentials.txt — NEVER COMMIT THIS FILE
# Generated by data_loader.sh on $(date)
# =============================================================================

EOF
}

creds_append() {
  echo "$1" >> "$CREDENTIALS_FILE"
}

# =============================================================================
# MAIN
# =============================================================================

echo "================================================================="
echo " Phase 2 Data Loader — multi-tenant"
echo "================================================================="
echo " Target: $BASE_URL"
echo ""

creds_reset

# ─────────────────────────────────────────────────────────────────────────────
# WAT Simmering (tenant 1)
# ─────────────────────────────────────────────────────────────────────────────
echo ">>> WAT Simmering (wat-simmering)"
WAT_SLUG="wat-simmering"
WAT_ADMIN_TOKEN=$(get_token "$WAT_SLUG" "admin.wat" "Admin#WAT2026")
echo "  Admin token: OK"

read -r WAT_TRAINER_A WAT_TRAINER_B WAT_MEMBER_A WAT_MEMBER_B < \
  <(load_tenant_data "$WAT_SLUG" "$WAT_ADMIN_TOKEN" "WAT-")

echo "  Linking Keycloak seed users"
link_users "$WAT_SLUG" "$WAT_ADMIN_TOKEN" \
  "member.anna" "Member#WAT2026" \
  "trainer.wat" "Trainer#WAT2026" \
  "$WAT_MEMBER_A" "$WAT_TRAINER_A"

echo "  Generating API key"
WAT_KEY_RESP=$(gql_auth "$WAT_ADMIN_TOKEN" 'mutation {
  generateApiKey(input: { label: "data-loader" }) { rawKey }
}')
WAT_API_KEY=$(echo "$WAT_KEY_RESP" | jq -r '.data.generateApiKey.rawKey')
echo "  API key: ${WAT_API_KEY:0:20}..."

creds_append "# --- WAT Simmering (wat-simmering) ---"
creds_append "WAT_ADMIN_USER=admin.wat"
creds_append "WAT_ADMIN_PASS=Admin#WAT2026"
creds_append "WAT_MEMBER_USER=member.anna"
creds_append "WAT_MEMBER_PASS=Member#WAT2026"
creds_append "WAT_TRAINER_USER=trainer.wat"
creds_append "WAT_TRAINER_PASS=Trainer#WAT2026"
creds_append "WAT_API_KEY=$WAT_API_KEY"
creds_append ""

echo ">>> WAT Simmering: done"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Union Rot-Weiss (tenant 2)
# ─────────────────────────────────────────────────────────────────────────────
echo ">>> Union Rot-Weiss (union-rot-weiss)"
URW_SLUG="union-rot-weiss"
URW_ADMIN_TOKEN=$(get_token "$URW_SLUG" "admin.urw" "Admin#URW2026")
echo "  Admin token: OK"

read -r URW_TRAINER_A URW_TRAINER_B URW_MEMBER_A URW_MEMBER_B < \
  <(load_tenant_data "$URW_SLUG" "$URW_ADMIN_TOKEN" "URW-")

echo "  Linking Keycloak seed users"
link_users "$URW_SLUG" "$URW_ADMIN_TOKEN" \
  "member.urw" "Member#URW2026" \
  "trainer.urw" "Trainer#URW2026" \
  "$URW_MEMBER_A" "$URW_TRAINER_A"

echo "  Generating API key"
URW_KEY_RESP=$(gql_auth "$URW_ADMIN_TOKEN" 'mutation {
  generateApiKey(input: { label: "data-loader" }) { rawKey }
}')
URW_API_KEY=$(echo "$URW_KEY_RESP" | jq -r '.data.generateApiKey.rawKey')
echo "  API key: ${URW_API_KEY:0:20}..."

creds_append "# --- Union Rot-Weiss (union-rot-weiss) ---"
creds_append "URW_ADMIN_USER=admin.urw"
creds_append "URW_ADMIN_PASS=Admin#URW2026"
creds_append "URW_MEMBER_USER=member.urw"
creds_append "URW_MEMBER_PASS=Member#URW2026"
creds_append "URW_TRAINER_USER=trainer.urw"
creds_append "URW_TRAINER_PASS=Trainer#URW2026"
creds_append "URW_API_KEY=$URW_API_KEY"
creds_append ""

echo ">>> Union Rot-Weiss: done"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# ASV Pressbaum Badminton (tenant 3)
# ─────────────────────────────────────────────────────────────────────────────
echo ">>> ASV Pressbaum Badminton (asv-pressbaum-badminton)"
ASV_SLUG="asv-pressbaum-badminton"
ASV_ADMIN_TOKEN=$(get_token "$ASV_SLUG" "admin.asv" "Admin#ASV2026")
echo "  Admin token: OK"

read -r ASV_TRAINER_A ASV_TRAINER_B ASV_MEMBER_A ASV_MEMBER_B < \
  <(load_tenant_data "$ASV_SLUG" "$ASV_ADMIN_TOKEN" "ASV-")

echo "  Linking Keycloak seed users"
link_users "$ASV_SLUG" "$ASV_ADMIN_TOKEN" \
  "member.asv" "Member#ASV2026" \
  "trainer.asv" "Trainer#ASV2026" \
  "$ASV_MEMBER_A" "$ASV_TRAINER_A"

echo "  Generating API key"
ASV_KEY_RESP=$(gql_auth "$ASV_ADMIN_TOKEN" 'mutation {
  generateApiKey(input: { label: "data-loader" }) { rawKey }
}')
ASV_API_KEY=$(echo "$ASV_KEY_RESP" | jq -r '.data.generateApiKey.rawKey')
echo "  API key: ${ASV_API_KEY:0:20}..."

creds_append "# --- ASV Pressbaum Badminton (asv-pressbaum-badminton) ---"
creds_append "ASV_ADMIN_USER=admin.asv"
creds_append "ASV_ADMIN_PASS=Admin#ASV2026"
creds_append "ASV_MEMBER_USER=member.asv"
creds_append "ASV_MEMBER_PASS=Member#ASV2026"
creds_append "ASV_TRAINER_USER=trainer.asv"
creds_append "ASV_TRAINER_PASS=Trainer#ASV2026"
creds_append "ASV_API_KEY=$ASV_API_KEY"
creds_append ""

echo ">>> ASV Pressbaum: done"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Verification (optional)
# ─────────────────────────────────────────────────────────────────────────────
if [[ "$VERIFY" == "--verify" ]]; then
  echo "==> Verification: WAT Simmering"
  echo "--- members ---"
  gql_auth "$WAT_ADMIN_TOKEN" '{ members { id firstName lastName currentStatus } }' \
    | jq '.data.members'

  echo "--- trainers ---"
  gql_auth "$WAT_ADMIN_TOKEN" '{ trainers { id firstName lastName } }' \
    | jq '.data.trainers'

  echo "--- apiKeys ---"
  gql_auth "$WAT_ADMIN_TOKEN" '{ apiKeys { id label active createdAt } }' \
    | jq '.data.apiKeys'

  echo ""
  echo "==> Verifying API key auth (WAT Simmering)"
  WAT_TENANT_VIA_KEY=$(curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    -H "x-api-key: $WAT_API_KEY" \
    -d '{"query": "{ currentTenant { slug name } }"}' \
    | jq '.data.currentTenant')
  echo "currentTenant via API key: $WAT_TENANT_VIA_KEY"
fi

echo "================================================================="
echo " Data load complete."
echo " Credentials saved to: $CREDENTIALS_FILE"
echo " *** DO NOT COMMIT $CREDENTIALS_FILE ***"
echo "================================================================="
