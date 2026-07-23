package at.mavila.dbchatbox.infrastructure.security;

/**
 * GraphQL response payload for {@code login} and {@code refreshToken} mutations.
 *
 * <p>Fields mirror the Keycloak token response. The caller must treat
 * {@code accessToken} and {@code refreshToken} as secrets.</p>
 *
 * @param accessToken  the short-lived JWT to pass as {@code Authorization: Bearer}
 * @param refreshToken the longer-lived token for obtaining a new access token
 * @param expiresIn    seconds until the access token expires
 * @param tokenType    typically {@code "Bearer"}
 * @since 2026-06-28
 */
public record AuthPayload(
    String accessToken,
    String refreshToken,
    int expiresIn,
    String tokenType
) {
}
