package at.mavila.dbchatbox.domain.chatbox.tools;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionService;
import lombok.RequiredArgsConstructor;

/**
 * AI tools for reading member-subscription data: what a given member is
 * subscribed to, which subscriptions are overdue, and which are pending
 * admin payment review.
 *
 * @since 2026-04-20
 */
@Component
@RequiredArgsConstructor
public class SubscriptionQueryTools {

  private final MemberSubscriptionService subscriptionService;

  /**
   * Lists a member's subscriptions. Handles questions like "what is Anna
   * subscribed to?", "show me John's active subscriptions".
   */
  @Tool(description = """
      List all subscriptions for a specific member.
      A subscription links a member to a membership type for a period, with an agreed price
      and a payment status (NOT_PAID, IN_REVIEW, REVIEWED).
      Optionally filter to only active subscriptions (those whose end date has not yet passed).
      """)
  public List<SubscriptionSummary> subscriptionsForMember(
      @ToolParam(description = "The member's TSID.") final Long memberId,
      @ToolParam(required = false, description = """
          Set to true to return only active subscriptions (end date >= today).
          Leave null or false to return every subscription the member has ever had.
          """) final Boolean activeOnly) {

    return subscriptionService.findByMember(memberId, activeOnly).stream()
        .map(this::toSummary)
        .toList();
  }

  /**
   * Returns subscriptions past their grace period that have not been marked
   * REVIEWED. Handles "which members are overdue?", "show me everyone who
   * hasn't paid past the deadline".
   */
  @Tool(description = """
      List subscriptions that are past their grace period and whose payment is not yet verified.
      Each entry includes how many days overdue the subscription is, the member's name and email,
      the membership type, and the current payment status.
      Use this to answer questions about overdue or delinquent payments.
      """)
  public List<OverdueSubscriptionSummary> overdueSubscriptions() {
    final LocalDate today = LocalDate.now();
    return subscriptionService.findOverdueSubscriptions().stream()
        .map(sub -> toOverdueSummary(sub, today))
        .toList();
  }

  /**
   * Returns subscriptions where a payment document has been uploaded and is
   * awaiting admin verification. Handles "what needs my review?".
   */
  @Tool(description = """
      List subscriptions that currently have a payment document awaiting admin verification
      (payment status IN_REVIEW). These are the subscriptions where a member has uploaded a proof
      of payment but an admin has not yet approved or rejected it.
      """)
  public List<SubscriptionSummary> pendingPaymentReviews() {
    return subscriptionService.findPendingPaymentReviews().stream()
        .map(this::toSummary)
        .toList();
  }

  // ---------------------------------------------------------------
  // mapping
  // ---------------------------------------------------------------

  private SubscriptionSummary toSummary(final MemberSubscription sub) {
    final Member member = sub.getMember();
    final MembershipType type = sub.getMembershipType();
    final LocalDate today = LocalDate.now();

    return new SubscriptionSummary(
        sub.getId(),
        isNull(member) ? null : member.getId(),
        isNull(member) ? null : "%s %s".formatted(member.getFirstName(), member.getLastName()),
        type.getName(),
        sub.getStartDate(),
        sub.getEndDate(),
        sub.getAgreedPrice(),
        sub.getPaymentStatus().name(),
        !sub.getEndDate().isBefore(today));
  }

  private OverdueSubscriptionSummary toOverdueSummary(final MemberSubscription sub, final LocalDate today) {
    final Member member = sub.getMember();
    final MembershipType type = sub.getMembershipType();
    final LocalDate dueDate = sub.getStartDate().plusDays(type.getGracePeriodDays());
    final long daysOverdue = ChronoUnit.DAYS.between(dueDate, today);

    return new OverdueSubscriptionSummary(
        sub.getId(),
        isNull(member) ? null : member.getId(),
        isNull(member) ? null : "%s %s".formatted(member.getFirstName(), member.getLastName()),
        isNull(member) ? null : member.getEmail(),
        type.getName(),
        sub.getStartDate(),
        sub.getEndDate(),
        dueDate,
        (int) Math.max(0L, daysOverdue),
        sub.getPaymentStatus().name());
  }

  // ---------------------------------------------------------------
  // DTOs
  // ---------------------------------------------------------------

  /**
   * Flat view of a {@link MemberSubscription}.
   */
  public record SubscriptionSummary(
      Long id,
      Long memberId,
      String memberName,
      String membershipTypeName,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal agreedPrice,
      String paymentStatus,
      boolean active) {
  }

  /**
   * Overdue subscription — includes the computed due date and days-overdue.
   */
  public record OverdueSubscriptionSummary(
      Long subscriptionId,
      Long memberId,
      String memberName,
      String memberEmail,
      String membershipTypeName,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate dueDate,
      int daysOverdue,
      String paymentStatus) {
  }
}
