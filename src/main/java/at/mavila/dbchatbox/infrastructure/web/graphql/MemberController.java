package at.mavila.dbchatbox.infrastructure.web.graphql;

import static java.util.Objects.nonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.member.CreateMemberCommand;
import at.mavila.dbchatbox.domain.club.member.KeycloakMemberService;
import at.mavila.dbchatbox.domain.club.member.MemberGdprService;
import at.mavila.dbchatbox.domain.club.member.MemberService;
import at.mavila.dbchatbox.domain.club.member.MemberStatusHistory;
import at.mavila.dbchatbox.domain.club.member.MemberView;
import at.mavila.dbchatbox.domain.club.member.Status;
import at.mavila.dbchatbox.domain.club.member.UpdateMemberCommand;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionService;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for member queries and mutations.
 *
 * <p>
 * Member identity is sourced from Keycloak via {@link KeycloakMemberService}; the {@link Member}
 * GraphQL type is backed by {@link MemberView}. Status history and status transitions remain
 * DB-backed via {@link MemberService}.
 * </p>
 *
 * <p>All operations require the {@code ADMIN} role — member PII (name, email, phone, dates) must
 * not be visible to regular members or trainers, and the {@code member} back-reference on
 * subscriptions/payments would otherwise leak that PII to non-admin callers.</p>
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MemberController {

  private final KeycloakMemberService keycloakMemberService;
  private final MemberService memberService;
  private final MemberGdprService memberGdprService;
  private final MemberSubscriptionService subscriptionService;

  // ==================== QUERIES ====================

  /**
   * Returns all members, optionally filtered by current status.
   *
   * @param status optional status filter (e.g. {@code ACTIVE}, {@code INACTIVE})
   * @return list of matching member views
   */
  @QueryMapping
  public List<MemberView> members(@Argument final String status) {
    final Status statusEnum = nonNull(status) ? Status.valueOf(status) : null;
    return keycloakMemberService.findAll(statusEnum);
  }

  /**
   * Returns a single member by ID.
   *
   * @param id the member ID
   * @return the member view
   */
  @QueryMapping
  public MemberView memberById(@Argument final Long id) {
    return keycloakMemberService.findById(id);
  }

  /**
   * Returns the full status audit trail for a member.
   *
   * @param memberId the member ID
   * @return list of status history entries ordered by date
   */
  @QueryMapping
  public List<MemberStatusHistory> memberStatusHistory(@Argument final Long memberId) {
    return memberService.getStatusHistory(memberId);
  }

  // ==================== MUTATIONS ====================

  /**
   * Registers a new member in Keycloak with an automatic {@code ACTIVE} status entry. Idempotent on
   * a pre-existing Keycloak email.
   *
   * @param input the member creation input
   * @return the created member view
   */
  @MutationMapping
  public MemberView createMember(@Argument final Map<String, Object> input) {
    final var command = new CreateMemberCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"), (LocalDate) input.get("memberSince"),
        (LocalDate) input.get("memberUntil"));
    return keycloakMemberService.createMember(command);
  }

  /**
   * Updates a member's contact and profile details in Keycloak.
   *
   * @param id    the member ID
   * @param input the update input
   * @return the updated member view
   */
  @MutationMapping
  public MemberView updateMember(@Argument final Long id, @Argument final Map<String, Object> input) {
    final var command = new UpdateMemberCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"), (LocalDate) input.get("memberSince"),
        (LocalDate) input.get("memberUntil"));
    return keycloakMemberService.updateMember(id, command);
  }

  /**
   * Records a status transition for a member with an optional reason.
   *
   * @param input the status change input
   * @return the new status history entry
   */
  @MutationMapping
  public MemberStatusHistory changeMemberStatus(@Argument final Map<String, Object> input) {
    return memberService.changeMemberStatus(Long.valueOf(input.get("memberId").toString()),
        Status.valueOf((String) input.get("status")), (String) input.get("reason"));
  }

  /**
   * Performs GDPR erasure — anonymises the Keycloak account, then runs the DB-side erasure
   * (status history, active subscription termination, {@code anonymized} flag).
   *
   * @param id the member ID
   * @return the erasure result with anonymised field details
   */
  @MutationMapping
  public MemberGdprService.DeleteMemberResult deleteMember(@Argument final Long id) {
    keycloakMemberService.anonymizeInKeycloak(id);
    return memberGdprService.deleteMember(id);
  }

  // ==================== TYPE RESOLVERS ====================

  /**
   * Resolves the current status name for a member from the latest status history entry.
   *
   * @param member the member view
   * @return the current status as a string
   */
  @SchemaMapping(typeName = "Member", field = "currentStatus")
  public String currentStatus(final MemberView member) {
    return keycloakMemberService.getCurrentStatus(member).name();
  }

  /**
   * Resolves subscriptions for a member, optionally filtered by active state.
   *
   * @param member the member view
   * @param active optional filter
   * @return list of matching subscriptions
   */
  @SchemaMapping(typeName = "Member", field = "subscriptions")
  public List<MemberSubscription> subscriptions(final MemberView member, @Argument final Boolean active) {
    return subscriptionService.findByMember(member.id(), active);
  }

  /**
   * Resolves the {@code member} field of a {@link MemberSubscription} to a Keycloak-sourced
   * {@link MemberView} (the JPA navigation returns only a lean stub with no PII).
   *
   * @param subscription the subscription
   * @return the member view
   */
  @SchemaMapping(typeName = "MemberSubscription", field = "member")
  public MemberView memberOnSubscription(final MemberSubscription subscription) {
    return keycloakMemberService.findById(subscription.getMember().getId());
  }

  /**
   * Resolves the {@code member} field of a {@link MemberPaymentStatus} to a Keycloak-sourced
   * {@link MemberView}.
   *
   * @param status the outstanding-payment row
   * @return the member view
   */
  @SchemaMapping(typeName = "MemberPaymentStatus", field = "member")
  public MemberView memberOnPaymentStatus(final MemberPaymentStatus status) {
    return keycloakMemberService.findById(status.member().getId());
  }

  /**
   * Resolves the {@code member} field of an {@link OverdueSubscription} to a Keycloak-sourced
   * {@link MemberView}.
   *
   * @param overdue the overdue-subscription row
   * @return the member view
   */
  @SchemaMapping(typeName = "OverdueSubscription", field = "member")
  public MemberView memberOnOverdueSubscription(final OverdueSubscription overdue) {
    return keycloakMemberService.findById(overdue.member().getId());
  }
}
