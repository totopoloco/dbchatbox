package at.mavila.dbchatbox.domain.club.notification;

import static java.util.Objects.nonNull;

import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import lombok.extern.slf4j.Slf4j;

/**
 * Logging-only mock implementation of {@link NotificationService} for Phase 1.
 *
 * <p>
 * Logs notification events at INFO level but does not send real emails. Domain
 * services depend on the
 * {@link NotificationService} interface, so when real email delivery is added
 * in a future phase, this
 * mock can be replaced via {@code @Primary} or a Spring profile without
 * changing any domain code.
 * </p>
 *
 * @since 2026-04-13
 */
@Component
@Slf4j
public class LoggingNotificationService implements NotificationService {

  /** {@inheritDoc} */
  @Override
  public void notifyOverduePayment(final MemberSubscription subscription) {
    log.info("[NOTIFICATION] Overdue payment alert — subscription={}, member={}, agreedPrice={}",
        subscription.getId(),
        nonNull(subscription.getMember()) ? subscription.getMember().getId() : "N/A",
        subscription.getAgreedPrice());
  }

  /** {@inheritDoc} */
  @Override
  public void sendPaymentReminder(final MemberSubscription subscription) {
    log.info("[NOTIFICATION] Payment reminder — subscription={}, member={}, paymentStatus={}",
        subscription.getId(),
        nonNull(subscription.getMember()) ? subscription.getMember().getId() : "N/A",
        subscription.getPaymentStatus());
  }

  /** {@inheritDoc} */
  @Override
  public void notifyPaymentDocumentUploaded(final MemberSubscription subscription) {
    log.info("[NOTIFICATION] Payment document uploaded — subscription={}, member={}, paymentStatus=IN_REVIEW",
        subscription.getId(),
        nonNull(subscription.getMember()) ? subscription.getMember().getId() : "N/A");
  }

  /** {@inheritDoc} */
  @Override
  public void notifyMembershipTypePublished(final MembershipType membershipType) {
    log.info("[NOTIFICATION] Membership type published — id={}, name={}, price={}",
        membershipType.getId(),
        membershipType.getName(),
        membershipType.getPrice());
  }
}
