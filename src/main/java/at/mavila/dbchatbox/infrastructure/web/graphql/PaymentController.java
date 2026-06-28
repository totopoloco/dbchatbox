package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.payment.Payment;
import at.mavila.dbchatbox.domain.club.payment.PaymentDocument;
import at.mavila.dbchatbox.domain.club.payment.PaymentDocumentService;
import at.mavila.dbchatbox.domain.club.payment.PaymentService;
import at.mavila.dbchatbox.domain.club.payment.RecordPaymentCommand;
import at.mavila.dbchatbox.domain.club.payment.ReviewPaymentDocumentCommand;
import at.mavila.dbchatbox.domain.club.payment.UploadPaymentDocumentCommand;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionService;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for payment queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PaymentController {

  private final PaymentService paymentService;
  private final PaymentDocumentService paymentDocumentService;
  private final MemberSubscriptionService subscriptionService;

  // ==================== QUERIES ====================

  /**
   * Returns all payments recorded against a specific subscription.
   *
   * @param memberSubscriptionId the subscription ID
   * @return list of payments for the subscription
   */
  @QueryMapping
  public List<Payment> paymentsBySubscription(@Argument final Long memberSubscriptionId) {
    return paymentService.findBySubscription(memberSubscriptionId);
  }

  /**
   * Returns all payments across all subscriptions for a member.
   *
   * @param memberId the member ID
   * @return list of payments for the member
   */
  @QueryMapping
  public List<Payment> paymentsByMember(@Argument final Long memberId) {
    return paymentService.findByMember(memberId);
  }

  /**
   * Returns active subscriptions with unpaid dues.
   *
   * @return list of outstanding payment summaries
   */
  @QueryMapping
  public List<Map<String, Object>> outstandingPayments() {
    return paymentService.findOutstandingPayments().stream().map(info -> {
      final MemberSubscription sub = info.subscription();
      final Member member = sub.getMember();
      final MembershipType type = sub.getMembershipType();

      return Map.<String, Object>of("member", member, "subscription", sub, "membershipType", type, "amountDue",
          sub.getAgreedPrice(), "amountPaid", info.amountPaid(), "outstanding", info.outstanding());
    }).toList();
  }

  // ==================== MUTATIONS ====================

  /**
   * Records a payment against a subscription.
   *
   * @param input the payment input containing subscription ID, amount, currency,
   *              date, and notes
   * @return the recorded payment
   */
  @MutationMapping
  public Payment recordPayment(@Argument final Map<String, Object> input) {
    final var command = new RecordPaymentCommand(Long.valueOf(input.get("memberSubscriptionId").toString()),
        new BigDecimal(input.get("amount").toString()), (String) input.get("currency"),
        (LocalDate) input.get("paymentDate"), (String) input.get("notes"));
    return paymentService.recordPayment(command);
  }

  /**
   * Uploads a payment proof document for a subscription, transitioning its
   * payment status from {@code NOT_PAID} to {@code IN_REVIEW}.
   *
   * @param input the upload input containing subscription ID, file name, Base64
   *              content, and notes
   * @return the created payment document
   */
  @MutationMapping
  public PaymentDocument uploadPaymentDocument(@Argument final Map<String, Object> input) {
    final var command = new UploadPaymentDocumentCommand(
        Long.valueOf(input.get("memberSubscriptionId").toString()),
        (String) input.get("fileName"),
        (String) input.get("fileContent"),
        (String) input.get("notes"));
    return paymentDocumentService.uploadDocument(command);
  }

  /**
   * Reviews a payment document — approves ({@code REVIEWED}) or rejects
   * ({@code NOT_PAID}).
   *
   * @param input the review input containing subscription ID, approval flag, and
   *              optional reason
   * @return the updated subscription
   */
  @MutationMapping
  public MemberSubscription reviewPaymentDocument(@Argument final Map<String, Object> input) {
    final var command = new ReviewPaymentDocumentCommand(
        Long.valueOf(input.get("memberSubscriptionId").toString()),
        (Boolean) input.get("approved"),
        (String) input.get("reason"));
    return paymentDocumentService.reviewDocument(command);
  }

  /**
   * Returns subscriptions past their grace period with payment status other than
   * {@code REVIEWED}.
   *
   * @return list of overdue subscription details
   */
  @QueryMapping
  public List<Map<String, Object>> overdueSubscriptions() {
    return subscriptionService.findOverdueSubscriptions().stream().map(sub -> {
      final Member member = sub.getMember();
      final MembershipType type = sub.getMembershipType();
      final LocalDate dueDate = sub.getStartDate().plusDays(type.getGracePeriodDays());
      final long daysOverdue = ChronoUnit.DAYS.between(dueDate, LocalDate.now());

      return Map.<String, Object>of(
          "member", member,
          "subscription", sub,
          "membershipType", type,
          "paymentStatus", sub.getPaymentStatus().name(),
          "dueDate", dueDate,
          "daysOverdue", (int) daysOverdue);
    }).toList();
  }

  /**
   * Returns subscriptions with payment status {@code IN_REVIEW} awaiting admin
   * verification.
   *
   * @return list of subscriptions pending payment review
   */
  @QueryMapping
  public List<MemberSubscription> pendingPaymentReviews() {
    return subscriptionService.findPendingPaymentReviews();
  }

  /**
   * Returns all payment documents uploaded for a subscription.
   *
   * @param memberSubscriptionId the subscription ID
   * @return list of payment documents
   */
  @QueryMapping
  public List<PaymentDocument> paymentDocuments(@Argument final Long memberSubscriptionId) {
    return paymentDocumentService.findBySubscription(memberSubscriptionId);
  }
}
