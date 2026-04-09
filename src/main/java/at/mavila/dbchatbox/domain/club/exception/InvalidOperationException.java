package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when a business rule is violated (generic catch-all for domain constraint violations).
 *
 * @since 2026-04-09
 */
public class InvalidOperationException extends RuntimeException {

  /**
   * Creates a new exception with a descriptive message.
   *
   * @param message
   *                  description of the violated rule
   */
  public InvalidOperationException(final String message) {
    super(message);
  }
}
