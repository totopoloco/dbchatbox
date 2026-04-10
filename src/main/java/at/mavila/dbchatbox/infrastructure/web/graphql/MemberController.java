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

  @QueryMapping
  public List<Member> members(@Argument
  final String status) {
    final Status statusEnum = nonNull(status) ? Status.valueOf(status) : null;
    return memberService.findAll(statusEnum);
  }

  @QueryMapping
  public Member memberById(@Argument
  final Long id) {
    return memberService.findById(id);
  }

  @QueryMapping
  public List<MemberStatusHistory> memberStatusHistory(@Argument
  final Long memberId) {
    return memberService.getStatusHistory(memberId);
  }

  // ==================== MUTATIONS ====================

  @MutationMapping
  public Member createMember(@Argument
  final Map<String, Object> input) {
    final var command = new CreateMemberCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"), (LocalDate) input.get("memberSince"),
        (LocalDate) input.get("memberUntil"));
    return memberService.createMember(command);
  }

  @MutationMapping
  public Member updateMember(@Argument
  final Long id, @Argument
  final Map<String, Object> input) {
    final var command = new UpdateMemberCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"), (LocalDate) input.get("memberSince"),
        (LocalDate) input.get("memberUntil"));
    return memberService.updateMember(id, command);
  }

  @MutationMapping
  public MemberStatusHistory changeMemberStatus(@Argument
  final Map<String, Object> input) {
    return memberService.changeMemberStatus(Long.valueOf(input.get("memberId").toString()),
        Status.valueOf((String) input.get("status")), (String) input.get("reason"));
  }

  @MutationMapping
  public Map<String, Object> deleteMember(@Argument
  final Long id) {
    final var result = memberGdprService.deleteMember(id);
    return Map.of("memberId", result.memberId().toString(), "anonymizedAt", result.anonymizedAt().toString(),
        "fieldsAnonymized", result.fieldsAnonymized());
  }

  // ==================== TYPE RESOLVERS ====================

  @SchemaMapping(typeName = "Member", field = "currentStatus")
  public String currentStatus(final Member member) {
    return memberService.getCurrentStatus(member).name();
  }

  @SchemaMapping(typeName = "Member", field = "subscriptions")
  public List<MemberSubscription> subscriptions(final Member member, @Argument
  final Boolean active) {
    return subscriptionService.findByMember(member.getId(), active);
  }
}
