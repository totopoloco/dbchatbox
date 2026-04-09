package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when a referenced member cannot be found.
 *
 * @since 2026-04-09
 */
public class MemberNotFoundException extends RuntimeException {

  /**
   * Creates a new exception for the given member ID.
   *
   * @param memberId
   *                   the missing member's ID
   */
  public MemberNotFoundException(final Long memberId) {
    super("Member not found: %d".formatted(memberId));
  }
}
