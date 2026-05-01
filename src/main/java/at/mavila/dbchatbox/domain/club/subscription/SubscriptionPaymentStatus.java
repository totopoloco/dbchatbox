package at.mavila.dbchatbox.domain.club.subscription;

/**
 * Payment-verification statuses for member subscriptions.
 *
 * <p>
 * Tracks whether a member has paid for a given subscription period.
 * Transitions: {@code NOT_PAID → IN_REVIEW → REVIEWED} or
 * {@code IN_REVIEW → NOT_PAID} (rejection).
 * </p>
 *
 * @since 2026-04-13
 */
public enum SubscriptionPaymentStatus {

  /**
   * Default — no payment document has been uploaded. Member owes the full agreed
   * price.
   */
  NOT_PAID,

  /**
   * Member has uploaded a payment document. Awaiting admin verification.
   */
  IN_REVIEW,

  /**
   * Admin has verified the payment document and confirmed payment.
   */
  REVIEWED
}
