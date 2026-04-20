package at.mavila.dbchatbox.domain.club.notification;

import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;

/**
 * Domain-level notification service interface.
 *
 * <p>
 * Defines notification triggers for the club management system. Domain services
 * depend on this interface,
 * never on the concrete implementation. Phase 1 provides a logging-only mock;
 * real email delivery
 * can be added in a future phase by replacing the implementation via
 * {@code @Primary} or a Spring profile.
 * </p>
 *
 * @since 2026-04-13
 */
public interface NotificationService {

  /**
   * Notifies admin that a subscription's grace period has expired and payment is
   * overdue.
   *
   * @param subscription the overdue subscription
   */
  void notifyOverduePayment(MemberSubscription subscription);

  /**
   * Sends a payment reminder to the member for an unpaid subscription.
   *
   * @param subscription the unpaid subscription
   */
  void sendPaymentReminder(MemberSubscription subscription);

  /**
   * Notifies admin that a member has uploaded a payment document for review.
   *
   * @param subscription the subscription with the uploaded document
   */
  void notifyPaymentDocumentUploaded(MemberSubscription subscription);

  /**
   * Notifies all active members that a membership type has been published (DRAFT
   * → ACTIVE).
   *
   * @param membershipType the newly activated membership type
   */
  void notifyMembershipTypePublished(MembershipType membershipType);
}
