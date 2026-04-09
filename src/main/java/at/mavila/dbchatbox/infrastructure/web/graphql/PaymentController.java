package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.member.MemberService;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.payment.Payment;
import at.mavila.dbchatbox.domain.club.payment.PaymentService;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for payment queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;
  private final MemberService memberService;

  // ==================== QUERIES ====================

  @QueryMapping
  public List<Payment> paymentsBySubscription(@Argument
  final Long memberSubscriptionId) {
    return paymentService.findBySubscription(memberSubscriptionId);
  }

  @QueryMapping
  public List<Payment> paymentsByMember(@Argument
  final Long memberId) {
    return paymentService.findByMember(memberId);
  }

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

  @MutationMapping
  public Payment recordPayment(@Argument
  final Map<String, Object> input) {
    return paymentService.recordPayment(Long.valueOf(input.get("memberSubscriptionId").toString()),
        new BigDecimal(input.get("amount").toString()), (String) input.get("currency"),
        (LocalDate) input.get("paymentDate"), (String) input.get("notes"));
  }
}
