/**
 * Membership domain — membership type definitions, pricing, duration, and
 * session linkage.
 *
 * <p>
 * Key components:
 * </p>
 * <ul>
 * <li>{@link at.mavila.dbchatbox.domain.club.membership.MembershipType} —
 * subscription template with price, duration, and grace period</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.membership.MembershipTypeService}
 * — creation, status transitions, and session management</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.membership.MembershipTypeStatus} —
 * lifecycle statuses (DRAFT, ACTIVE, INACTIVE)</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.membership.Unit} — time units for
 * membership duration</li>
 * </ul>
 *
 * @since 2026-04-09
 */
package at.mavila.dbchatbox.domain.club.membership;
