package at.mavila.dbchatbox.infrastructure.web.graphql;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.membership.CreateMembershipTypeCommand;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeService;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeStatus;
import at.mavila.dbchatbox.domain.club.membership.Unit;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for membership type queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
public class MembershipController {

  private final MembershipTypeService membershipTypeService;

  // ==================== QUERIES ====================

  @QueryMapping
  public List<MembershipType> membershipTypes(@Argument
  final String status) {
    final MembershipTypeStatus statusEnum = nonNull(status) ? MembershipTypeStatus.valueOf(status) : null;
    return membershipTypeService.findAll(statusEnum);
  }

  // ==================== MUTATIONS ====================

  @MutationMapping
  public MembershipType createMembershipType(@Argument
  final Map<String, Object> input) {
    final var command = new CreateMembershipTypeCommand((String) input.get("name"), (String) input.get("description"),
        new BigDecimal(input.get("price").toString()), ((Number) input.get("duration")).intValue(),
        Unit.valueOf((String) input.get("unit")),
        input.containsKey("proratedMode") ? (Boolean) input.get("proratedMode") : null);
    return membershipTypeService.create(command);
  }

  @MutationMapping
  public MembershipType changeMembershipTypeStatus(@Argument
  final Long id, @Argument
  final String status) {
    return membershipTypeService.changeStatus(id, MembershipTypeStatus.valueOf(status));
  }

  @MutationMapping
  public MembershipType assignSessionToMembership(@Argument
  final Long membershipTypeId, @Argument
  final Long sessionId) {
    return membershipTypeService.assignSession(membershipTypeId, sessionId);
  }

  @MutationMapping
  public MembershipType removeSessionFromMembership(@Argument
  final Long membershipTypeId, @Argument
  final Long sessionId) {
    return membershipTypeService.removeSession(membershipTypeId, sessionId);
  }
}
