package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when an invalid status transition is attempted.
 *
 * @since 2026-04-09
 */
public class InvalidStatusTransitionException extends RuntimeException {

  /**
   * Creates a new exception describing the invalid transition.
   *
   * @param from
   *               the current status
   * @param to
   *               the attempted target status
   */
  public InvalidStatusTransitionException(final String from, final String to) {
    super("Invalid status transition: %s → %s".formatted(from, to));
  }
}
