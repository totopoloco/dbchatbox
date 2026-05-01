package at.mavila.dbchatbox.domain.chatbox.tools;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.payment.Payment;
import at.mavila.dbchatbox.domain.club.payment.PaymentService;
import at.mavila.dbchatbox.domain.club.payment.PaymentService.OutstandingPaymentInfo;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import lombok.RequiredArgsConstructor;

/**
 * AI tools that read payment data — individual payments for a subscription or
 * member, and aggregate "who owes money" views.
 *
 * @since 2026-04-20
 */
@Component
@RequiredArgsConstructor
public class PaymentQueryTools {

  private final PaymentService paymentService;

  /**
   * All payments for a specific subscription.
   */
  @Tool(description = """
      List every payment recorded against a single subscription.
      Each entry has the amount, currency, payment date, and an optional note.
      Use this when the question is scoped to one subscription ("what payments has John made for his Gold plan?").
      """)
  public List<PaymentSummary> paymentsForSubscription(
      @ToolParam(description = "The subscription's TSID.") final Long memberSubscriptionId) {
    return paymentService.findBySubscription(memberSubscriptionId).stream()
        .map(this::toPaymentSummary)
        .toList();
  }

  /**
   * All payments made by a specific member, across all subscriptions.
   */
  @Tool(description = """
      List every payment made by a member across all of their subscriptions.
      Use this for "show me Anna's payment history" and similar per-member questions.
      """)
  public List<PaymentSummary> paymentsForMember(
      @ToolParam(description = "The member's TSID.") final Long memberId) {
    return paymentService.findByMember(memberId).stream()
        .map(this::toPaymentSummary)
        .toList();
  }

  /**
   * Aggregate "who still owes something" view across all active subscriptions.
   * This is the tool the LLM should pick for the canonical user request:
   * "show me all members who haven't paid yet this period".
   */
  @Tool(description = """
      List every active subscription that still has an outstanding balance — i.e. the amount paid
      is less than the agreed price. Each entry contains the member, the membership type,
      the agreed price, the amount paid so far, the remaining outstanding amount, and the
      payment status.
      Use this for questions like "who hasn't paid yet?", "show me all members with unpaid subscriptions",
      "who owes the club money this month?".
      """)
  public List<OutstandingPaymentSummary> outstandingPayments() {
    return paymentService.findOutstandingPayments().stream()
        .map(this::toOutstandingSummary)
        .toList();
  }

  // ---------------------------------------------------------------
  // mapping
  // ---------------------------------------------------------------

  private PaymentSummary toPaymentSummary(final Payment payment) {
    final MemberSubscription sub = payment.getMemberSubscription();
    return new PaymentSummary(
        payment.getId(),
        isNull(sub) ? null : sub.getId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getPaymentDate(),
        payment.getNotes());
  }

  private OutstandingPaymentSummary toOutstandingSummary(final OutstandingPaymentInfo info) {
    final MemberSubscription sub = info.subscription();
    final Member member = sub.getMember();
    return new OutstandingPaymentSummary(
        sub.getId(),
        isNull(member) ? null : member.getId(),
        isNull(member) ? null : "%s %s".formatted(member.getFirstName(), member.getLastName()),
        sub.getMembershipType().getName(),
        sub.getAgreedPrice(),
        info.amountPaid(),
        info.outstanding(),
        sub.getEndDate(),
        sub.getPaymentStatus().name());
  }

  // ---------------------------------------------------------------
  // DTOs
  // ---------------------------------------------------------------

  /**
   * Flat view of a {@link Payment}.
   */
  public record PaymentSummary(
      Long id,
      Long subscriptionId,
      BigDecimal amount,
      String currency,
      LocalDate paymentDate,
      String notes) {
  }

  /**
   * "Who still owes" row — merges subscription + aggregated payment totals.
   */
  public record OutstandingPaymentSummary(
      Long subscriptionId,
      Long memberId,
      String memberName,
      String membershipTypeName,
      BigDecimal agreedPrice,
      BigDecimal amountPaid,
      BigDecimal outstanding,
      LocalDate subscriptionEndDate,
      String paymentStatus) {
  }
}
