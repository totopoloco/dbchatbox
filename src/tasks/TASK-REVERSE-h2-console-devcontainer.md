# TASK-REVERSE: H2 Console Accessible from Windows Host

**Status:** Complete

> Code was written first; this document records the decision, the files touched, and the tests
> still needed. "REVERSE" = implementation preceded the task.

---

## Problem

Developers on Windows accessing the devcontainer over port-forwarding saw two failure modes on
`http://localhost:8080/h2-console/`:

1. **"remote connections ('webAllowOthers') are disabled"** — H2's embedded web servlet rejected
   requests not originating from `localhost` inside the container.
2. **"localhost refused to connect" in each frame** — Spring Security's default
   `X-Frame-Options: DENY` header prevented the H2 console's internal frameset from rendering.

Both issues are scoped to the `dev` profile only; production uses PostgreSQL and the console is
never enabled there.

## What Was Built

### `webAllowOthers` property
**File:** `src/main/resources/application-dev.properties`

```properties
spring.h2.console.settings.web-allow-others=true
```

Instructs H2's `WebServlet` to accept requests from any origin, not just `127.0.0.1`. Only
effective in the `dev` profile where `spring.h2.console.enabled=true`.

### `frameOptions.sameOrigin()`
**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/SecurityConfig.java`

```java
.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
```

Changes `X-Frame-Options` from `DENY` to `SAMEORIGIN`. The H2 console's own sub-pages load in
frames from the same origin (`localhost:8080`), so `SAMEORIGIN` allows them while still blocking
embedding from external origins. This setting is global (not dev-profile-gated in Java code)
because `SAMEORIGIN` is a reasonable default and H2 console is only enabled via the profile flag.

---

## Tests Still Needed

These are manual / devcontainer smoke tests rather than unit tests:

- `GET /h2-console/` from the Windows browser → login page renders without error.
- H2 console login with `jdbc:h2:mem:dbchatbox` / user `sa` / empty password → main console
  with frames renders correctly.
- Frame content loads (tree, query pane visible) — confirms `SAMEORIGIN` fix.
