package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when an operation is attempted on a member that has been GDPR-deleted.
 *
 * @since 2026-04-09
 */
public class MemberDeletedException extends RuntimeException {

  /**
   * Creates a new exception for the given member ID.
   *
   * @param memberId
   *                   the deleted member's ID
   */
  public MemberDeletedException(final Long memberId) {
    super("Member has been deleted (GDPR): %d".formatted(memberId));
  }
}
