# TASK-REVERSE: Graceful Invalid-Tenant Error in `login` / `refreshToken`

**Status:** Complete

> Code was written first; this document records the decision, the files touched, and the tests
> still needed. "REVERSE" = implementation preceded the task.

---

## Problem

Calling `refreshToken` (or `login`) with a blank or unknown `tenantSlug` produced an opaque
`INTERNAL_ERROR` GraphQL error instead of an actionable message. The root cause was either a
`NullPointerException` when `tenantSlug` was null (bypassing all handlers) or the generic
`ResourceNotFoundException` being the wrong abstraction for a caller-input problem.

## What Was Built

### `InvalidTenantException`
**File:** `src/main/java/at/mavila/dbchatbox/domain/club/exception/InvalidTenantException.java`

A dedicated `RuntimeException` for blank, unknown, or inactive tenant slugs. Keeps the exception
vocabulary explicit rather than reusing `ResourceNotFoundException` (a not-found concept) for what
is fundamentally a bad-input problem.

### `TenantService.requireBySlug` guard
**File:** `src/main/java/at/mavila/dbchatbox/domain/club/tenant/TenantService.java`

Added a null/blank guard before querying the database:
```java
if (isNull(slug) || slug.isBlank()) {
    throw new InvalidTenantException("Tenant slug must not be blank");
}
return tenantRepository.findBySlugAndActiveIsTrue(slug)
    .orElseThrow(() -> new InvalidTenantException("Invalid or unknown tenant: %s".formatted(slug)));
```

Both `login` and `refreshToken` in `AuthController` call `requireBySlug`, so both benefit
without controller changes.

### `GraphQlExceptionAdvice.handleInvalidTenant`
**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/GraphQlExceptionAdvice.java`

Maps `InvalidTenantException` to `ValidationError` so the GraphQL response surfaces a structured,
user-readable error instead of `INTERNAL_ERROR`.

---

## Tests Still Needed

- `refreshToken` with a blank `tenantSlug` → `ValidationError` with message "Tenant slug must not be blank".
- `refreshToken` with a well-formed but unknown slug → `ValidationError` with message "Invalid or unknown tenant: …".
- `login` same two cases.
- Happy path unaffected: valid slug still reaches Keycloak.
