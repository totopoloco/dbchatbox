package at.mavila.dbchatbox.domain.club.trainer;

/**
 * Approval statuses for trainer hour submissions.
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 *
 * <ul>
 * <li>{@link #PENDING} — submitted, awaiting admin approval</li>
 * <li>{@link #APPROVED} — approved, trainer entitled to payment (terminal)</li>
 * <li>{@link #REJECTED} — rejected by admin, trainer can resubmit</li>
 * </ul>
 *
 * @since 2026-04-09
 */
public enum TrainerLogStatus {
  PENDING, APPROVED, REJECTED
}
