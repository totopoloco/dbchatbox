package at.mavila.dbchatbox.domain.club.membership;

/**
 * Lifecycle statuses for membership types.
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 *
 * <ul>
 * <li>{@link #DRAFT} — being set up, not yet available for subscriptions</li>
 * <li>{@link #ACTIVE} — live, new subscriptions can be created</li>
 * <li>{@link #INACTIVE} — discontinued, no new subscriptions, existing ones continue</li>
 * </ul>
 *
 * <p>
 * Valid transitions: DRAFT→ACTIVE, ACTIVE→INACTIVE, INACTIVE→ACTIVE, DRAFT→INACTIVE. Transitioning back to DRAFT is not
 * allowed.
 * </p>
 *
 * @since 2026-04-09
 */
public enum MembershipTypeStatus {
  DRAFT, ACTIVE, INACTIVE
}
