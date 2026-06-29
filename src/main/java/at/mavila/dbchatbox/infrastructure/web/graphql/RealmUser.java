package at.mavila.dbchatbox.infrastructure.web.graphql;

/**
 * GraphQL response type for a Keycloak realm user, sourced from the Admin REST API.
 *
 * <p>Field names mirror the {@code RealmUser} type in {@code realm.graphqls}.</p>
 *
 * @since 2026-06-28
 */
public record RealmUser(
    String id,
    String username,
    String firstName,
    String lastName,
    String email,
    boolean enabled
) {
}
