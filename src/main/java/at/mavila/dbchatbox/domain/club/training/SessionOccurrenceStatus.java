package at.mavila.dbchatbox.domain.club.training;

/**
 * Lifecycle statuses for individual session occurrences.
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 *
 * <ul>
 * <li>{@link #SCHEDULED} — planned and upcoming</li>
 * <li>{@link #CANCELLED} — cancelled (terminal)</li>
 * <li>{@link #COMPLETED} — took place, trainers can log hours (terminal)</li>
 * </ul>
 *
 * @since 2026-04-09
 */
public enum SessionOccurrenceStatus {
  SCHEDULED, CANCELLED, COMPLETED
}
