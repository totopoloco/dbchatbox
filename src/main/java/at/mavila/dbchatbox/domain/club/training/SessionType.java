package at.mavila.dbchatbox.domain.club.training;

/**
 * Categories of club sessions.
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 *
 * <ul>
 * <li>{@link #TRAINING} — coach-led session requiring an assigned trainer</li>
 * <li>{@link #FREE_GAME} — open play session with no trainer required</li>
 * </ul>
 *
 * @since 2026-04-09
 */
public enum SessionType {
  TRAINING, FREE_GAME
}
