package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when a tenant slug is blank, unknown, or inactive.
 *
 * <p>Mapped to {@code ValidationError} in {@code GraphQlExceptionAdvice} so callers
 * receive a structured error instead of {@code INTERNAL_ERROR}.</p>
 *
 * @since 2026-06-28
 */
public class InvalidTenantException extends RuntimeException {

    /**
     * @param message user-safe description of why the tenant is invalid
     */
    public InvalidTenantException(final String message) {
        super(message);
    }
}
