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

  /**
   * Records a payment against a subscription.
   *
   * @param memberSubscriptionId
   *                               the subscription ID
   * @param amount
   *                               the payment amount (must be positive)
   * @param currency
   *                               the currency code (defaults to EUR)
   * @param paymentDate
   *                               the payment date
   * @param notes
   *                               optional notes
   * @return the created payment
   * @throws ResourceNotFoundException
   *                                     if the subscription does not exist
   * @throws InvalidOperationException
   *                                     if the subscription has ended or amount is not positive
   */
  public Payment recordPayment(final Long memberSubscriptionId, final BigDecimal amount, final String currency,
      final LocalDate paymentDate, final String notes) {
    final MemberSubscription subscription = subscriptionRepository.findById(memberSubscriptionId)
        .orElseThrow(() -> new ResourceNotFoundException("MemberSubscription", memberSubscriptionId));

    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidOperationException("Payment amount must be positive");
    }

    if (subscription.getEndDate().isBefore(LocalDate.now())) {
      throw new InvalidOperationException("Cannot record payment for an ended subscription");
    }

    final Payment payment = Payment.builder().memberSubscription(subscription).amount(amount)
        .currency(isNull(currency) ? "EUR" : currency).paymentDate(paymentDate).notes(notes).build();

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
