# TASK: Add CORS Configuration for the GraphQL Endpoint

**Status:** Complete

---

## Problem

The frontend receives a CORS error when calling the GraphQL endpoint (`/graphql`). The application
has no CORS configuration — Spring Boot's default denies cross-origin requests, so browsers block
every preflight `OPTIONS` request from a different origin.

---

## Goal

Allow the frontend origin(s) to call `/graphql` (and the GraphiQL playground at `/graphiql`) while
keeping all other origins blocked.

---

## Task List

### T1 — Add `app.cors.*` properties

**File:** `src/main/resources/application.properties`

Add a dedicated property group so allowed origins are externally configurable and never hardcoded:

```properties
# CORS — comma-separated list of allowed origins for the GraphQL endpoint
# Override per profile (application-dev.properties, application-prod.properties)
app.cors.allowed-origins=http://localhost:3000
app.cors.allowed-methods=GET,POST,OPTIONS
app.cors.allowed-headers=*
app.cors.allow-credentials=true
app.cors.max-age-seconds=3600
```

Override in the appropriate profile files:

- **`application-dev.properties`** — `http://localhost:3000` (or whatever port the local frontend
  uses)
- **`application-prod.properties`** — the production frontend URL(s)

### T2 — Create `CorsProperties` configuration-properties record

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/CorsProperties.java`

```java
@ConfigurationProperties(prefix = "app.cors")
@Validated
public record CorsProperties(
    @NotEmpty(message = "At least one allowed origin must be configured")
    List<String> allowedOrigins,

    @NotEmpty(message = "At least one allowed method must be configured")
    List<String> allowedMethods,

    List<String> allowedHeaders,

    boolean allowCredentials,

    @Min(0)
    long maxAgeSeconds
) {}
```

### T3 — Create `CorsConfiguration` `@Configuration` class

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/CorsWebConfiguration.java`

Implement `WebMvcConfigurer` and register a CORS mapping for `/graphql` and `/graphiql` using the
values from `CorsProperties`:

```java
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class CorsWebConfiguration implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/graphql")
            .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
            .allowedMethods(corsProperties.allowedMethods().toArray(String[]::new))
            .allowedHeaders(corsProperties.allowedHeaders().toArray(String[]::new))
            .allowCredentials(corsProperties.allowCredentials())
            .maxAge(corsProperties.maxAgeSeconds());

        registry.addMapping("/graphiql")
            .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "OPTIONS")
            .allowCredentials(corsProperties.allowCredentials())
            .maxAge(corsProperties.maxAgeSeconds());
    }
}
```

### T4 — Add Spring for GraphQL CORS property (belt-and-suspenders)

Spring for GraphQL has its own CORS layer that runs before the MVC one. Add this to
`application.properties` to keep them in sync:

```properties
spring.graphql.cors.allowed-origins=${app.cors.allowed-origins}
spring.graphql.cors.allowed-methods=${app.cors.allowed-methods}
spring.graphql.cors.allow-credentials=${app.cors.allow-credentials}
spring.graphql.cors.max-age=${app.cors.max-age-seconds}s
```

### T5 — Write tests

**New file:** `src/test/java/at/mavila/dbchatbox/infrastructure/web/CorsWebConfigurationTest.java`

Cover:

- A preflight `OPTIONS /graphql` request from an allowed origin returns `200` with the correct
  `Access-Control-Allow-Origin` header.
- A preflight `OPTIONS /graphql` request from a **disallowed** origin returns `403` (no CORS
  headers).
- The `CorsProperties` bean fails validation when `allowedOrigins` is empty.

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` (or `MockMvc`).

---

## Security Notes

- **Never use `allowedOrigins("*")` together with `allowCredentials(true)`** — this combination is
  rejected by browsers and blocked by Spring. Use explicit origin lists.
- Avoid wildcards in `allowedHeaders` in production; enumerate only the headers the frontend
  actually sends (`Content-Type`, `Authorization`, etc.).
- Keep the production origin list as restrictive as possible — one domain per deployment.

---

## Acceptance Criteria

- [x] Frontend can execute GraphQL queries and mutations without CORS errors.
- [x] `OPTIONS /graphql` preflight from a non-listed origin returns `403`.
- [x] Allowed origins are read from properties, not hardcoded in Java.
- [x] All new classes have Javadoc.
- [x] Tests pass (`./gradlew test`).
