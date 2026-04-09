package at.mavila.dbchatbox.domain.club.trainer;

/**
 * Compensation modes for trainers.
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 *
 * <ul>
 * <li>{@link #PER_SESSION} — paid after each approved session</li>
 * <li>{@link #MONTHLY} — approved hours aggregated and settled monthly</li>
 * </ul>
 *
 * @since 2026-04-09
 */
public enum TrainerPaymentMode {
  PER_SESSION, MONTHLY
}
