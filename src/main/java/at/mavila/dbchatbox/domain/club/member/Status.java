package at.mavila.dbchatbox.domain.club.member;

/**
 * Enumeration of possible member statuses within the club.
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 *
 * <ul>
 * <li>{@link #ACTIVE} — member is active and in good standing</li>
 * <li>{@link #INACTIVE} — member has been deactivated (soft-delete)</li>
 * <li>{@link #DELETED} — member data has been anonymized per GDPR Art. 17 (terminal status)</li>
 * </ul>
 *
 * @since 2026-04-09
 */
public enum Status {
  ACTIVE, INACTIVE, DELETED
}
