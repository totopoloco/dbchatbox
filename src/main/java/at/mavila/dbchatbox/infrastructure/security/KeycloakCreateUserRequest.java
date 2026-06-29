package at.mavila.dbchatbox.infrastructure.security;

import java.util.List;
import java.util.Map;

/**
 * Request body for creating a Keycloak user via {@code POST /admin/realms/{realm}/users}.
 *
 * <p>
 * Field names mirror the Keycloak {@code UserRepresentation} so Jackson serialises them directly.
 * {@code attributes} carries the club-managed custom attributes ({@code memberId}, {@code phoneNumber},
 * {@code memberSince}, {@code memberUntil}, {@code updatedAt}) as Keycloak's
 * {@code Map<String, List<String>>} shape. A top-level record (not an inner type) because it is shared
 * between {@link KeycloakAdminClient} and the domain {@code KeycloakMemberService}.
 * </p>
 *
 * @param username
 *                      the login username
 * @param firstName
 *                      given name
 * @param lastName
 *                      family name
 * @param email
 *                      email address
 * @param enabled
 *                      whether the account is enabled
 * @param emailVerified
 *                      whether the email is pre-verified
 * @param attributes
 *                      custom user attributes
 * @since 2026-06-29
 */
public record KeycloakCreateUserRequest(
    String username,
    String firstName,
    String lastName,
    String email,
    boolean enabled,
    boolean emailVerified,
    Map<String, List<String>> attributes) {
}
