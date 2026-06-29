package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when a Keycloak Admin REST API call fails in a way that the caller
 * should be informed about (e.g. insufficient permissions to list realm users).
 *
 * <p>Mapped to {@code DataFetchingException} in {@code GraphQlExceptionAdvice}.</p>
 *
 * @since 2026-06-28
 */
public class KeycloakAdminException extends RuntimeException {

    /**
     * @param message user-safe description of the failure
     */
    public KeycloakAdminException(final String message) {
        super(message);
    }
}
