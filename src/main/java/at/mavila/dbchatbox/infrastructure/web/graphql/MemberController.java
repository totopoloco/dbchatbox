package at.mavila.dbchatbox.infrastructure.web.graphql;

import static java.util.Objects.nonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.member.CreateMemberCommand;
import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.member.MemberGdprService;
import at.mavila.dbchatbox.domain.club.member.MemberService;
import at.mavila.dbchatbox.domain.club.member.MemberStatusHistory;
import at.mavila.dbchatbox.domain.club.member.Status;
import at.mavila.dbchatbox.domain.club.member.UpdateMemberCommand;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionService;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for member queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;
  private final MemberGdprService memberGdprService;
  private final MemberSubscriptionService subscriptionService;

  // ==================== QUERIES ====================

  /**
   * Returns all members, optionally filtered by current status.
   *
   * @param status optional status filter (e.g. {@code ACTIVE}, {@code INACTIVE})
   * @return list of matching members
   */
  @QueryMapping
  public List<Member> members(@Argument final String status) {
    final Status statusEnum = nonNull(status) ? Status.valueOf(status) : null;
    return memberService.findAll(statusEnum);
  }

  /**
   * Returns a single member by ID.
   *
   * @param id the member ID
   * @return the member
   */
  @QueryMapping
  public Member memberById(@Argument final Long id) {
    return memberService.findById(id);
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
   * Registers a new member with an automatic {@code ACTIVE} status entry.
   *
   * @param input the member creation input
   * @return the created member
   */
  @MutationMapping
  public Member createMember(@Argument final Map<String, Object> input) {
    final var command = new CreateMemberCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"), (LocalDate) input.get("memberSince"),
        (LocalDate) input.get("memberUntil"));
    return memberService.createMember(command);
  }

  /**
   * Updates a member's contact and profile details.
   *
   * @param id    the member ID
   * @param input the update input
   * @return the updated member
   */
  @MutationMapping
  public Member updateMember(@Argument final Long id, @Argument final Map<String, Object> input) {
    final var command = new UpdateMemberCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"), (LocalDate) input.get("memberSince"),
        (LocalDate) input.get("memberUntil"));
    return memberService.updateMember(id, command);
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
   * Performs GDPR erasure — anonymises personal data and ends active
   * subscriptions.
   *
   * @param id the member ID
   * @return the erasure result with anonymised field details
   */
  @MutationMapping
  public Map<String, Object> deleteMember(@Argument final Long id) {
    final var result = memberGdprService.deleteMember(id);
    return Map.of("memberId", result.memberId().toString(), "anonymizedAt", result.anonymizedAt().toString(),
        "fieldsAnonymized", result.fieldsAnonymized());
  }

  // ==================== TYPE RESOLVERS ====================

  /**
   * Resolves the current status name for a member from the latest status history
   * entry.
   *
   * @param member the member
   * @return the current status as a string
   */
  @SchemaMapping(typeName = "Member", field = "currentStatus")
  public String currentStatus(final Member member) {
    return memberService.getCurrentStatus(member).name();
  }

  /**
   * Resolves subscriptions for a member, optionally filtered by active state.
   *
   * @param member the member
   * @param active optional filter
   * @return list of matching subscriptions
   */
  @SchemaMapping(typeName = "Member", field = "subscriptions")
  public List<MemberSubscription> subscriptions(final Member member, @Argument final Boolean active) {
    return subscriptionService.findByMember(member.getId(), active);
  }
}
