# TASK: Keycloak Service in the Devcontainer + Realm Provisioning

**Status:** Pending

> Stands up Keycloak in `.devcontainer/docker-compose.yml` and provisions one realm per tenant
> (`wat-simmering`, `union-rot-weiss`, `asv-pressbaum-badminton`) with roles, clients, and demo users,
> imported automatically on startup. Implements the identity-provider half of
> [ClubManagement-Phase2-Multitenancy-Auth](../specs/club/ClubManagement-Phase2-Multitenancy-Auth.md)
> §§ Keycloak realm model, D1.

---

## Goal

After `docker compose up`, three fully-configured Keycloak realms are available at
`http://localhost:8088`, each with `ADMIN`/`MEMBER`/`TRAINER` roles, a public SPA client, a confidential
M2M client, and demo users — so the backend can validate tokens and a developer can log in immediately.

---

## Scope

- Edit `.devcontainer/docker-compose.yml` (add a `keycloak` service on the existing `dev-network`).
- New realm-import JSON files under `.devcontainer/keycloak/import/`.
- No application code in this task.

---

## Task List

### T1 — Add the Keycloak service to `docker-compose.yml`

**File:** `.devcontainer/docker-compose.yml`

Add the service below alongside the existing `dbchatbox-dev` and `postgres` services, on the existing
`dev-network`. Host port **8088** → container 8080 (8080 on the host is the Spring app). Keycloak runs
in dev mode and imports realms on boot.

```yaml
  keycloak:
    image: quay.io/keycloak/keycloak:26.4
    command: ["start-dev", "--import-realm"]
    environment:
      # Bootstrap admin (Keycloak 26+). Older tags use KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD.
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      KC_HTTP_ENABLED: "true"
      KC_HEALTH_ENABLED: "true"
      # Keep the issuer stable & matching Tenant.issuer_uri (spec rule 96).
      KC_HOSTNAME: http://localhost:8088
      KC_HOSTNAME_STRICT: "false"
    ports:
      - "8088:8080"
    volumes:
      - ./keycloak/import:/opt/keycloak/data/import:ro
    networks:
      - dev-network
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/8080 && echo done"]
      interval: 10s
      timeout: 5s
      retries: 10
```

> **Issuer consistency (spec rule 96).** The dev workflow runs the app on the **host**
> (`./gradlew bootRun`), so it reaches Keycloak at `http://localhost:8088` and tokens carry
> `iss = http://localhost:8088/realms/<realm>` — exactly the `issuer_uri` seeded in V7. If you later run
> the app *inside* the compose network, it would reach Keycloak at `http://keycloak:8080` and the issuer
> would differ; keep `KC_HOSTNAME`, the backend's `app.keycloak.base-url`, and `Tenant.issuer_uri` all
> pointing at the same base URL.

No new volume is required (the import dir is a bind mount). Keycloak dev mode uses an embedded H2 store,
which is fine for the PoC; realms are re-imported from JSON on each boot.

---

### T2 — Realm import: WAT Simmering (template)

**New file:** `.devcontainer/keycloak/import/wat-simmering-realm.json`

This is the canonical realm. The other two are copies with names/secrets/users changed (T3). Key points:
realm roles `ADMIN`/`MEMBER`/`TRAINER`; public `club-spa` with **Direct Access Grants enabled** (the
GraphQL `login` uses the password grant) and PKCE; confidential `club-m2m` with **Service Accounts
enabled** holding an `M2M` role; three demo users with passwords and role mappings.

```json
{
  "realm": "wat-simmering",
  "enabled": true,
  "sslRequired": "none",
  "roles": {
    "realm": [
      { "name": "ADMIN",   "description": "Tenant administrator" },
      { "name": "MEMBER",  "description": "Club member" },
      { "name": "TRAINER", "description": "Club trainer" },
      { "name": "M2M",     "description": "Machine-to-machine service account" }
    ]
  },
  "clients": [
    {
      "clientId": "club-spa",
      "name": "Club SPA (frontend)",
      "publicClient": true,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": true,
      "redirectUris": ["http://localhost:3000/*"],
      "webOrigins": ["http://localhost:3000"],
      "attributes": { "pkce.code.challenge.method": "S256" }
    },
    {
      "clientId": "club-m2m",
      "name": "Club M2M (machine clients)",
      "publicClient": false,
      "serviceAccountsEnabled": true,
      "standardFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "secret": "wat-m2m-secret-CHANGE_ME",
      "serviceAccountClientRoles": {},
      "attributes": {}
    }
  ],
  "users": [
    {
      "username": "admin.wat",
      "enabled": true,
      "email": "admin@wat-simmering.example",
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "Admin#WAT2026", "temporary": false }],
      "realmRoles": ["ADMIN"]
    },
    {
      "username": "trainer.wat",
      "enabled": true,
      "email": "trainer@wat-simmering.example",
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "Trainer#WAT2026", "temporary": false }],
      "realmRoles": ["TRAINER"]
    },
    {
      "username": "member.anna",
      "enabled": true,
      "email": "anna@wat-simmering.example",
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "Member#WAT2026", "temporary": false }],
      "realmRoles": ["MEMBER"]
    }
  ]
}
```

> The `M2M` realm role must be granted to `club-m2m`'s **service account**. Realm-import does this via
> the service-account user (`service-account-club-m2m`) or `serviceAccountClientRoles`/realm-role
> mapping; if the import does not attach it cleanly, add the mapping once in the admin console and
> re-export. The default token mappers already emit `realm_access.roles`, `preferred_username`, `email`,
> and `sub` — no custom mapper is needed for Phase 2.

---

### T3 — Realm imports: Union Rot-Weiss & ASV Pressbaum Badminton

**New files:**
`.devcontainer/keycloak/import/union-rot-weiss-realm.json`,
`.devcontainer/keycloak/import/asv-pressbaum-badminton-realm.json`

Copy T2 and change only:

| Field            | union-rot-weiss                | asv-pressbaum-badminton              |
| ---------------- | ------------------------------ | ------------------------------------ |
| `realm`          | `union-rot-weiss`              | `asv-pressbaum-badminton`            |
| admin user       | `admin.urw` / `Admin#URW2026`  | `admin.asv` / `Admin#ASV2026`        |
| trainer user     | `trainer.urw` / `Trainer#URW2026` | `trainer.asv` / `Trainer#ASV2026` |
| member user      | `member.urw` / `Member#URW2026`| `member.asv` / `Member#ASV2026`      |
| `club-m2m` secret| `urw-m2m-secret-CHANGE_ME`     | `asv-m2m-secret-CHANGE_ME`           |
| user emails      | `*@union-rot-weiss.example`    | `*@asv-pressbaum-badminton.example`  |

Clients (`club-spa`, `club-m2m`) and roles are identical across realms.

---

### T4 — Document the M2M secrets for the backend

The backend (Variant A of the API-key design) needs each realm's `club-m2m` secret to perform
client-credentials calls. For the PoC, expose them to the app via env (never commit real secrets):

**File:** `.env.example` (append)

```
# Keycloak M2M client secrets (per realm). Match the realm import JSON.
KC_M2M_SECRET_WAT_SIMMERING=wat-m2m-secret-CHANGE_ME
KC_M2M_SECRET_UNION_ROT_WEISS=urw-m2m-secret-CHANGE_ME
KC_M2M_SECRET_ASV_PRESSBAUM=asv-m2m-secret-CHANGE_ME

# API key HMAC pepper (used by the x-api-key filter)
API_KEY_HMAC_SECRET=dev-only-change-me
```

> If Variant B of the API-key design is chosen ([TASK-m2m-api-key](./TASK-m2m-api-key.md)), the M2M
> secrets are not needed at runtime, but keep `club-m2m` in the realms so an upgrade to Variant A is
> config-only.

---

### T5 — Verify the realms

After `docker compose up keycloak`, confirm (manually or in `data_loader.sh`, next task):

```bash
# Discovery doc resolves (issuer must equal Tenant.issuer_uri):
curl -s http://localhost:8088/realms/wat-simmering/.well-known/openid-configuration | jq .issuer
# -> "http://localhost:8088/realms/wat-simmering"

# Password grant returns a token for the admin user:
curl -s -X POST http://localhost:8088/realms/wat-simmering/protocol/openid-connect/token \
  -d grant_type=password -d client_id=club-spa \
  -d username=admin.wat -d password='Admin#WAT2026' | jq -r .access_token
```

Decode the access token and confirm `realm_access.roles` contains `ADMIN` and `iss` matches.

---

## Security Notes

- **Dev only.** `start-dev`, `KC_HOSTNAME_STRICT=false`, `sslRequired: none`, and the bootstrap
  `admin/admin` are acceptable for the PoC devcontainer **only**. Production uses `start` with HTTPS, a
  real hostname, an external database, and rotated secrets.
- **Secrets.** Realm JSON files contain demo passwords and placeholder client secrets and are dev
  fixtures. Do not reuse these values anywhere real. The credentials file is git-ignored (next task).
- **Redirect URIs** are pinned to `http://localhost:3000` (the SPA). Keep them tight even in dev.

---

## Acceptance Criteria

- [ ] `docker compose up` starts a `keycloak` service reachable at `http://localhost:8088`.
- [ ] Three realms import automatically, each with `ADMIN`/`MEMBER`/`TRAINER`/`M2M` roles, `club-spa`
      (direct access grants on) and `club-m2m` (service accounts on), and three demo users.
- [ ] The discovery `issuer` for each realm equals the `issuer_uri` seeded in V7
      ([TASK-tenant-domain](./TASK-tenant-domain.md)).
- [ ] A password-grant call returns a token whose `realm_access.roles` reflects the user's role.
- [ ] `.env.example` documents the M2M secrets and the API-key HMAC pepper.
