package at.mavila.dbchatbox.domain.club.payment;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionRepository;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for payment management — recording and querying payments, outstanding calculations.
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final MemberSubscriptionRepository subscriptionRepository;
  private final CommandValidator commandValidator;

  /**
   * Records a payment against a subscription.
   *
   * @param command
   *                  the record-payment command
   * @return the created payment
   * @throws ResourceNotFoundException
   *                                     if the subscription does not exist
   * @throws InvalidOperationException
   *                                     if the subscription has ended or amount is not positive
   */
  public Payment recordPayment(final RecordPaymentCommand command) {
    commandValidator.validate(command);

    final MemberSubscription subscription = subscriptionRepository.findById(command.memberSubscriptionId())
        .orElseThrow(() -> new ResourceNotFoundException("MemberSubscription", command.memberSubscriptionId()));

    if (subscription.getEndDate().isBefore(LocalDate.now())) {
      throw new InvalidOperationException("Cannot record payment for an ended subscription");
    }

    final Payment payment = Payment.builder().memberSubscription(subscription).amount(command.amount())
        .currency(isNull(command.currency()) ? "EUR" : command.currency()).paymentDate(command.paymentDate())
        .notes(command.notes()).build();

    return paymentRepository.save(payment);
  }

  /**
   * Finds all payments for a specific subscription.
   *
   * @param memberSubscriptionId
   *                               the subscription ID
   * @return payments for the subscription
   */
  @Transactional(readOnly = true)
  public List<Payment> findBySubscription(final Long memberSubscriptionId) {
    return paymentRepository.findByMemberSubscriptionId(memberSubscriptionId);
  }

  /**
   * Finds all payments for a member across all their subscriptions.
   *
   * @param memberId
   *                   the member ID
   * @return all payments for the member
   */
  @Transactional(readOnly = true)
  public List<Payment> findByMember(final Long memberId) {
    return paymentRepository.findByMemberId(memberId);
  }

  /**
   * Returns outstanding payment information for all active subscriptions.
   *
   * @return list of outstanding payment statuses
   */
  @Transactional(readOnly = true)
  public List<OutstandingPaymentInfo> findOutstandingPayments() {
    final LocalDate today = LocalDate.now();
    return subscriptionRepository.findByEndDateGreaterThanEqual(today).stream().map(this::toOutstandingInfo)
        .filter(info -> info.outstanding().compareTo(BigDecimal.ZERO) > 0).toList();
  }

  private OutstandingPaymentInfo toOutstandingInfo(final MemberSubscription subscription) {
    final BigDecimal amountPaid = paymentRepository.sumAmountByMemberSubscriptionId(subscription.getId());
    final BigDecimal outstanding = subscription.getAgreedPrice().subtract(amountPaid);
    return new OutstandingPaymentInfo(subscription, amountPaid, outstanding);
  }

  /**
   * Information about outstanding payments for a subscription.
   *
   * @param subscription
   *                       the subscription
   * @param amountPaid
   *                       total amount paid
   * @param outstanding
   *                       remaining amount due
   */
  public record OutstandingPaymentInfo(MemberSubscription subscription, BigDecimal amountPaid, BigDecimal outstanding) {
  }
}
