package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when Keycloak rejects a login or token-refresh attempt with HTTP 401.
 *
 * <p>Mapped to {@code ValidationError} in {@code GraphQlExceptionAdvice} so callers
 * receive a structured error instead of an opaque {@code INTERNAL_ERROR}.</p>
 *
 * @since 2026-07-23
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * @param message user-safe description of the failure
     */
    public InvalidCredentialsException(final String message) {
        super(message);
    }
}
