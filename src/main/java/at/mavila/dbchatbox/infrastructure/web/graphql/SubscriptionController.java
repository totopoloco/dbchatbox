package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionService;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for subscription queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
public class SubscriptionController {

  private final MemberSubscriptionService subscriptionService;

  // ==================== QUERIES ====================

  @QueryMapping
  public List<MemberSubscription> memberSubscriptions(@Argument
  final Long memberId, @Argument
  final Boolean active) {
    return subscriptionService.findByMember(memberId, active);
  }

  // ==================== MUTATIONS ====================

  @MutationMapping
  public MemberSubscription subscribeMember(@Argument
  final Map<String, Object> input) {
    return subscriptionService.subscribeMember(Long.valueOf(input.get("memberId").toString()),
        Long.valueOf(input.get("membershipTypeId").toString()), (LocalDate) input.get("startDate"),
        (LocalDate) input.get("endDate"),
        input.containsKey("agreedPrice") ? new BigDecimal(input.get("agreedPrice").toString()) : null);
  }

  @MutationMapping
  public MemberSubscription endSubscription(@Argument
  final Long id) {
    return subscriptionService.endSubscription(id);
  }

  // ==================== TYPE RESOLVERS ====================

  @SchemaMapping(typeName = "MemberSubscription", field = "active")
  public boolean active(final MemberSubscription subscription) {
    return !subscription.getEndDate().isBefore(LocalDate.now());
  }
}
