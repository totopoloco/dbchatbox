package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Read projection of a member assembled from the Keycloak Admin REST API plus the lean DB stub.
 *
 * <p>
 * This is the type returned by {@link KeycloakMemberService} and the backing object for the GraphQL
 * {@code Member} type — its accessor names line up with the schema fields so Spring for GraphQL
 * resolves them directly. The {@code id} carries the TSID (Keycloak {@code memberId} attribute)
 * so {@code @SchemaMapping} resolvers can still look up status history and subscriptions by member
 * id. {@code keycloakSubject} carries the Keycloak user UUID for Admin API write-backs.
 * </p>
 *
 * @param id
 *                        the member TSID (equals the {@code memberId} Keycloak attribute)
 * @param keycloakSubject
 *                        the Keycloak user id ({@code sub})
 * @param firstName
 *                        given name (Keycloak)
 * @param lastName
 *                        family name (Keycloak)
 * @param email
 *                        email address (Keycloak)
 * @param phoneNumber
 *                        phone number (Keycloak custom attribute), nullable
 * @param memberSince
 *                        the date the person became a member (Keycloak custom attribute)
 * @param memberUntil
 *                        the date membership ends (Keycloak custom attribute), nullable
 * @param createdAt
 *                        account creation timestamp (Keycloak {@code createdTimestamp})
 * @param updatedAt
 *                        last profile update timestamp (Keycloak {@code updatedAt} custom attribute)
 * @since 2026-06-29
 */
public record MemberView(
    Long id,
    String keycloakSubject,
    String firstName,
    String lastName,
    String email,
    String phoneNumber,
    LocalDate memberSince,
    LocalDate memberUntil,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
}
