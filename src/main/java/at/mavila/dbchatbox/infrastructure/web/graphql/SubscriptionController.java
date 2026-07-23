package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.member.KeycloakMemberService;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionService;
import at.mavila.dbchatbox.domain.club.subscription.SubscribeMemberCommand;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for subscription queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SubscriptionController {

  private final MemberSubscriptionService subscriptionService;
  private final KeycloakMemberService keycloakMemberService;

  // ==================== QUERIES ====================

  /**
   * Returns subscriptions for a member, optionally filtered by active state.
   *
   * <p>Validates that the member exists in the current tenant's Keycloak realm before
   * querying subscriptions — the DB {@code member} stub is not a reliable existence check
   * because Keycloak realm-import fixtures have no stub row.</p>
   *
   * @param memberId the member TSID
   * @param active   optional filter — {@code true} for active only, {@code false} or
   *                 {@code null} for all
   * @return list of matching subscriptions, possibly empty
   */
  @QueryMapping
  public List<MemberSubscription> memberSubscriptions(@Argument final Long memberId, @Argument final Boolean active) {
    keycloakMemberService.findById(memberId);
    return subscriptionService.findByMember(memberId, active);
  }

  // ==================== MUTATIONS ====================

  /**
   * Subscribes a member to a membership type for a specific period.
   *
   * @param input the subscription input
   * @return the created subscription
   */
  @MutationMapping
  public MemberSubscription subscribeMember(@Argument final Map<String, Object> input) {
    final var command = new SubscribeMemberCommand(Long.valueOf(input.get("memberId").toString()),
        Long.valueOf(input.get("membershipTypeId").toString()), (LocalDate) input.get("startDate"),
        (LocalDate) input.get("endDate"),
        input.containsKey("agreedPrice") ? new BigDecimal(input.get("agreedPrice").toString()) : null);
    return subscriptionService.subscribeMember(command);
  }

  /**
   * Ends a subscription early by setting its end date to today.
   *
   * @param id the subscription ID
   * @return the updated subscription
   */
  @MutationMapping
  public MemberSubscription endSubscription(@Argument final Long id) {
    return subscriptionService.endSubscription(id);
  }

  // ==================== TYPE RESOLVERS ====================

  /**
   * Resolves whether a subscription is currently active
   * ({@code endDate >= today}).
   *
   * @param subscription the subscription to check
   * @return {@code true} if the subscription is active
   */
  @SchemaMapping(typeName = "MemberSubscription", field = "active")
  public boolean active(final MemberSubscription subscription) {
    return !subscription.getEndDate().isBefore(LocalDate.now());
  }

  /**
   * Resolves the payment status name for a subscription.
   *
   * @param subscription the subscription
   * @return the payment status as a string
   */
  @SchemaMapping(typeName = "MemberSubscription", field = "paymentStatus")
  public String paymentStatus(final MemberSubscription subscription) {
    return subscription.getPaymentStatus().name();
  }
}
