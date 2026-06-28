# TASK: Tenant-Aware Data Loader & Credentials File

**Status:** Pending

> Enhances `scripts/data_loader.sh` so it authenticates to Keycloak, loads demo data **per tenant** with
> a bearer token, links Keycloak users to their `Member`/`Trainer`, generates an API key per tenant, and
> writes a git-ignored credentials file. Implements
> [ClubManagement-Phase2-Multitenancy-Auth](../specs/club/ClubManagement-Phase2-Multitenancy-Auth.md)
> § Tenant Credentials File and supports rules 84, 95.
>
> **Depends on:** all prior Phase 2 tasks (the API must enforce auth and tenancy, the realms must exist,
> `login`/`generateApiKey`/`linkAppUser` must be implemented).

---

## Problem

The Phase 1 `data_loader.sh` posts to `/graphql` with no auth and assumes a single tenant. With Phase 2
every mutation is tenant-scoped and requires a token, so the loader must obtain a per-tenant admin token,
send it on every call, and repeat the whole demo dataset for each of the three tenants. Developers also
need a single place listing all demo credentials.

## Goal

Running `./scripts/data_loader.sh` (with Keycloak + the app up) populates all three tenants with
realistic, isolated demo data; links the demo member/trainer Keycloak users to their domain rows;
generates one API key per tenant; and writes `scripts/keycloak-credentials.txt`. `--verify` still works,
now per tenant.

---

## Scope

- Modify `scripts/data_loader.sh` only (plus a one-line `.gitignore` addition).
- Reuse the existing `gql()` / `gql_field()` helpers; add token handling and a tenant loop.
- No application code.

---

## Task List

### T1 — Keep existing helpers; add auth + per-tenant config

**File:** `scripts/data_loader.sh`

Keep the existing header, `set -euo pipefail`, guards, `BASE_URL`, and the `gql()` / `gql_field()`
helpers. Add Keycloak config and a per-tenant credential table near the top.

```bash
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8088}"
SPA_CLIENT_ID="${SPA_CLIENT_ID:-club-spa}"
CRED_FILE="$(dirname "$0")/keycloak-credentials.txt"

# Demo admins per tenant (must match the realm import JSONs).
# Format: "<slug>|<display name>|<admin user>|<admin password>"
TENANTS=(
  "wat-simmering|WAT Simmering|admin.wat|Admin#WAT2026"
  "union-rot-weiss|Union Rot-Weiss|admin.urw|Admin#URW2026"
  "asv-pressbaum-badminton|ASV Pressbaum Badminton|admin.asv|Admin#ASV2026"
)

# Demo member/trainer users per tenant (for linkAppUser).
# Format: "<slug>|<member user>|<member pw>|<trainer user>|<trainer pw>"
TENANT_USERS=(
  "wat-simmering|member.anna|Member#WAT2026|trainer.wat|Trainer#WAT2026"
  "union-rot-weiss|member.urw|Member#URW2026|trainer.urw|Trainer#URW2026"
  "asv-pressbaum-badminton|member.asv|Member#ASV2026|trainer.asv|Trainer#ASV2026"
)
```

---

### T2 — `kc_token()` and an authenticated `gql_auth()`

Add a helper that fetches an access token via the password grant, and make the GraphQL calls send it.
Simplest approach: a module-level `TOKEN` variable set per tenant, and have `gql()`/`gql_field()` include
`Authorization: Bearer $TOKEN` when `TOKEN` is non-empty (so existing call sites keep working).

```bash
# Fetch an access token for a realm user (password grant via club-spa).
# Usage: TOKEN=$(kc_token <realm> <username> <password>)
kc_token() {
  local realm="$1" user="$2" pass="$3" resp
  resp=$(curl -s -X POST "$KEYCLOAK_URL/realms/$realm/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" -d "client_id=$SPA_CLIENT_ID" \
    --data-urlencode "username=$user" --data-urlencode "password=$pass")
  if ! echo "$resp" | jq -e '.access_token' >/dev/null 2>&1; then
    echo "ERROR: could not obtain token for $user@$realm:" >&2
    echo "$resp" | jq '{error, error_description}' >&2
    exit 1
  fi
  echo "$resp" | jq -r '.access_token'
}
```

Then update the existing `gql()` to attach the bearer header when `TOKEN` is set (one-line change):

```bash
gql() {
  local query="$1" payload
  payload=$(jq -n --arg q "$query" '{"query": $q}')
  curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
    -d "$payload"
}
TOKEN=""   # default empty so pre-auth verify calls (e.g. discovery) still work
```

`gql_field()` is unchanged (it calls `gql()`).

---

### T3 — Pre-flight: verify Keycloak discovery + issuer

Before loading, confirm each realm's discovery issuer matches what the backend expects (spec rule 95/96):

```bash
echo "==> Verifying Keycloak realms"
for entry in "${TENANTS[@]}"; do
  IFS='|' read -r slug name admin pw <<< "$entry"
  iss=$(curl -s "$KEYCLOAK_URL/realms/$slug/.well-known/openid-configuration" | jq -r '.issuer // empty')
  [[ "$iss" == "$KEYCLOAK_URL/realms/$slug" ]] \
    && echo "  OK   $slug  -> $iss" \
    || { echo "  FAIL $slug  issuer='$iss' (expected $KEYCLOAK_URL/realms/$slug)"; exit 1; }
done
```

---

### T4 — Wrap the existing dataset in a per-tenant function

Refactor the current T2–T11 body (trainers, membership types, sessions, occurrences, members,
subscriptions, payments, documents, trainer logs) into a shell function `load_tenant_data` that runs
against whatever `TOKEN` is currently set. The dataset is identical per tenant (emails are now unique
*per tenant*, so the same `anna@…` is fine across realms — spec rule 79).

```bash
load_tenant_data() {
  local slug="$1"
  echo "==> [$slug] loading demo data"
  # ... the existing T2..T11 mutations, unchanged, now authenticated via $TOKEN ...
  # capture the IDs you need for linking (e.g. MEMBER_ANNA, TRAINER_*) into variables
  # that T5 (linkAppUser) and the per-tenant API key step can read.
}
```

> Minimal-diff strategy: leave the existing mutation blocks where they are but indent them inside this
> function, and replace the previously top-level ID variables with function-local ones returned via
> globals or `declare -g` so T5 can use them.

---

### T5 — Link Keycloak users to Member/Trainer (`linkAppUser`)

After loading a tenant's data, link the demo member and trainer Keycloak **subjects** to the domain rows
just created, so the Phase 1 self-isolation rules work for those logins (rule 84). Get each user's `sub`
from a token for that user, then call `linkAppUser` as the tenant admin.

```bash
link_users() {
  local slug="$1" member_user="$2" member_pw="$3" trainer_user="$4" trainer_pw="$5"
  local member_sub trainer_sub admin_token="$TOKEN"

  # Subject (sub) of each user = the 'sub' claim of their own token.
  member_sub=$(kc_token "$slug" "$member_user" "$member_pw" \
    | cut -d. -f2 | base64 -d 2>/dev/null | jq -r '.sub')
  trainer_sub=$(kc_token "$slug" "$trainer_user" "$trainer_pw" \
    | cut -d. -f2 | base64 -d 2>/dev/null | jq -r '.sub')

  TOKEN="$admin_token"   # link as admin
  gql_field ".data.linkAppUser.id" \
    "mutation { linkAppUser(input: { keycloakSubject: \"$member_sub\", memberId: \"$MEMBER_ANNA\" }) { id } }" \
    >/dev/null
  gql_field ".data.linkAppUser.id" \
    "mutation { linkAppUser(input: { keycloakSubject: \"$trainer_sub\", trainerId: \"$TRAINER_PRIMARY\" }) { id } }" \
    >/dev/null
  echo "  [$slug] linked member.$member_user and trainer.$trainer_user"
}
```

> JWT base64url decoding in bash: the `cut -d. -f2 | base64 -d` trick works for most tokens; if padding
> errors occur, pad the segment to a multiple of 4 or use `jq -R 'gsub("-";"+")|gsub("_";"/")'` before
> decode. Keep this best-effort — linking is a demo convenience.

---

### T6 — Generate one API key per tenant

As the tenant admin, generate an API key and capture the raw value (shown once) for the credentials file:

```bash
gen_api_key() {
  local slug="$1"
  API_KEY_RAW=$(gql_field ".data.generateApiKey.rawKey" \
    "mutation { generateApiKey(input: { label: \"nightly-export\", scope: \"READ\" }) { rawKey } }")
  echo "  [$slug] generated API key (stored in $CRED_FILE)"
}
```

---

### T7 — Main loop

Drive everything per tenant, then write the credentials file:

```bash
: > "$CRED_FILE"     # truncate
{
  echo "# Club Management — demo credentials (DEV ONLY — do not commit)"
  echo "# Keycloak: $KEYCLOAK_URL  (admin console: admin / admin)"
  echo ""
} >> "$CRED_FILE"

for i in "${!TENANTS[@]}"; do
  IFS='|' read -r slug name admin_user admin_pw          <<< "${TENANTS[$i]}"
  IFS='|' read -r _    member_user member_pw trainer_user trainer_pw <<< "${TENANT_USERS[$i]}"

  TOKEN=$(kc_token "$slug" "$admin_user" "$admin_pw")     # authenticate as tenant admin

  load_tenant_data "$slug"
  link_users    "$slug" "$member_user" "$member_pw" "$trainer_user" "$trainer_pw"
  gen_api_key   "$slug"

  {
    echo "[tenant] $name   slug=$slug   realm=$slug"
    echo "  ADMIN    $admin_user   / $admin_pw"
    echo "  TRAINER  $trainer_user / $trainer_pw"
    echo "  MEMBER   $member_user  / $member_pw"
    echo "  M2M      client_id=club-m2m   secret=<from realm import / .env>"
    echo "  API KEY  nightly-export   $API_KEY_RAW"
    echo ""
  } >> "$CRED_FILE"
done

TOKEN=""   # reset
echo "==> Wrote credentials to $CRED_FILE"
```

---

### T8 — `--verify` per tenant

Update the existing `--verify` block to run inside the tenant loop (or a second loop), authenticating as
each tenant's admin and asserting the reads return that tenant's data only. Add one cross-tenant check
to demonstrate isolation:

```bash
if [[ "$VERIFY" == "--verify" ]]; then
  for entry in "${TENANTS[@]}"; do
    IFS='|' read -r slug name admin_user admin_pw <<< "$entry"
    TOKEN=$(kc_token "$slug" "$admin_user" "$admin_pw")
    echo ""; echo "--- [$slug] members ---"
    gql '{ members { id firstName lastName currentStatus } }' | jq '.data.members'
    # ... the other existing verify queries, now per tenant ...
  done
  TOKEN=""
fi
```

---

### T9 — `.gitignore`

**File:** `.gitignore` (append)

```gitignore
# Phase 2 — local dev secrets / generated credentials
scripts/keycloak-credentials.txt
.env
```

---

## Security Notes

- **The credentials file is a dev fixture.** It contains demo passwords and freshly-generated API keys.
  It is git-ignored and must never be committed or reused outside the devcontainer.
- **API keys are shown once.** The loader captures each `rawKey` at creation and writes it to the file;
  it cannot be retrieved later (only its hash is stored — see [TASK-m2m-api-key](./TASK-m2m-api-key.md)).
- **Tokens stay in memory.** Do not write access/refresh tokens to disk or logs; only the static demo
  passwords and the generated API keys go in the file.
- **Isolation is verifiable.** The per-tenant `--verify` (and the cross-tenant check) demonstrate that
  each admin sees only their own tenant — the loader is also a smoke test for tenant scoping.

---

## Acceptance Criteria

- [ ] With Keycloak + the app running, `./scripts/data_loader.sh` loads all three tenants, each
      authenticated as its own admin.
- [ ] Pre-flight verifies each realm's discovery `issuer` matches `KEYCLOAK_URL/realms/<slug>`
      (fails fast on mismatch — rule 95/96).
- [ ] Demo member/trainer users are linked to their `Member`/`Trainer` via `linkAppUser`.
- [ ] One API key per tenant is generated and its raw value captured.
- [ ] `scripts/keycloak-credentials.txt` is written in the documented format and is git-ignored.
- [ ] `--verify` runs per tenant and shows tenant-isolated results; a cross-tenant probe returns nothing.
- [ ] Existing `gql()`/`gql_field()` helpers are reused; the script still exits non-zero on the first
      GraphQL/curl error.
