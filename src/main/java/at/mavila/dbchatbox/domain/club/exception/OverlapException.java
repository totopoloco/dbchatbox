package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when a schedule overlap is detected (trainer or location).
 *
 * @since 2026-04-09
 */
public class OverlapException extends RuntimeException {

  /**
   * Creates a new overlap exception.
   *
   * @param message
   *                  description of the overlap
   */
  public OverlapException(final String message) {
    super(message);
  }
}
