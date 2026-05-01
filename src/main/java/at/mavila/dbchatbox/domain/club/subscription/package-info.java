/**
 * Subscription domain — member subscriptions, payment status tracking, and
 * overdue detection.
 *
 * <p>
 * Key components:
 * </p>
 * <ul>
 * <li>{@link at.mavila.dbchatbox.domain.club.subscription.MemberSubscription} —
 * links a member to a membership type for a period</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionService}
 * — subscription lifecycle and overdue queries</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.subscription.SubscriptionPaymentStatus}
 * — payment verification statuses (NOT_PAID, IN_REVIEW, REVIEWED)</li>
 * </ul>
 *
 * @since 2026-04-09
 */
package at.mavila.dbchatbox.domain.club.subscription;
