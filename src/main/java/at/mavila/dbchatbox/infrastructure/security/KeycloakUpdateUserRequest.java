package at.mavila.dbchatbox.infrastructure.security;

import java.util.List;
import java.util.Map;

/**
 * Request body for updating a Keycloak user via {@code PUT /admin/realms/{realm}/users/{id}}.
 *
 * <p>
 * Field names mirror the Keycloak {@code UserRepresentation} so Jackson serialises them directly.
 * Keycloak replaces the {@code attributes} map wholesale on update, so callers must send the full
 * desired set of custom attributes (after merging unchanged values). A top-level record (not an
 * inner type) because it is shared between {@link KeycloakAdminClient} and the domain
 * {@code KeycloakMemberService}.
 * </p>
 *
 * @param firstName
 *                   given name
 * @param lastName
 *                   family name
 * @param email
 *                   email address
 * @param enabled
 *                   whether the account is enabled (set {@code false} to disable on GDPR erasure)
 * @param attributes
 *                   the full custom attribute set to persist
 * @since 2026-06-29
 */
public record KeycloakUpdateUserRequest(
    String firstName,
    String lastName,
    String email,
    boolean enabled,
    Map<String, List<String>> attributes) {
}
