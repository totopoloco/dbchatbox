package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when attempting to create or update an entity with an email that is already in use by another entity of the
 * same type.
 *
 * @since 2026-04-09
 */
public class DuplicateEmailException extends RuntimeException {

  /**
   * Creates a new exception with the duplicate email address.
   *
   * @param email
   *                the duplicate email
   */
  public DuplicateEmailException(final String email) {
    super("Email already in use: %s".formatted(email));
  }
}
