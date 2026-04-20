package at.mavila.dbchatbox.domain.club.membership;

/**
 * Enumeration of time units used for membership duration.
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via
 * {@code @Enumerated(EnumType.STRING)}.
 * </p>
 *
 * @since 2026-04-09
 */
public enum Unit {
  /** Days. */
  DAYS,
  /** Weeks. */
  WEEKS,
  /** Months. */
  MONTHS,
  /** Years. */
  YEARS
}
