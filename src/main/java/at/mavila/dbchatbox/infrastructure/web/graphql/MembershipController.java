package at.mavila.dbchatbox.infrastructure.web.graphql;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("isAuthenticated()")
public class MembershipController {

  private final MembershipTypeService membershipTypeService;

  // ==================== QUERIES ====================

  /**
   * Returns all membership types, optionally filtered by status.
   *
   * @param status optional status filter (e.g. {@code ACTIVE}, {@code DRAFT})
   * @return list of matching membership types
   */
  @QueryMapping
  public List<MembershipType> membershipTypes(@Argument final String status) {
    final MembershipTypeStatus statusEnum = nonNull(status) ? MembershipTypeStatus.valueOf(status) : null;
    return membershipTypeService.findAll(statusEnum);
  }

  // ==================== MUTATIONS ====================

  /**
   * Creates a new membership type in {@code DRAFT} status.
   *
   * @param input the creation input
   * @return the created membership type
   */
  @MutationMapping
  public MembershipType createMembershipType(@Argument final Map<String, Object> input) {
    final var command = new CreateMembershipTypeCommand((String) input.get("name"), (String) input.get("description"),
        new BigDecimal(input.get("price").toString()), ((Number) input.get("duration")).intValue(),
        Unit.valueOf((String) input.get("unit")),
        input.containsKey("proratedMode") ? (Boolean) input.get("proratedMode") : null,
        input.containsKey("gracePeriodDays") ? ((Number) input.get("gracePeriodDays")).intValue() : null);
    return membershipTypeService.create(command);
  }

  /**
   * Transitions a membership type to a new status.
   *
   * @param id     the membership type ID
   * @param status the target status
   * @return the updated membership type
   */
  @MutationMapping
  public MembershipType changeMembershipTypeStatus(@Argument final Long id, @Argument final String status) {
    return membershipTypeService.changeStatus(id, MembershipTypeStatus.valueOf(status));
  }

  /**
   * Links a session to a membership type.
   *
   * @param membershipTypeId the membership type ID
   * @param sessionId        the session ID
   * @return the updated membership type
   */
  @MutationMapping
  public MembershipType assignSessionToMembership(@Argument final Long membershipTypeId,
      @Argument final Long sessionId) {
    return membershipTypeService.assignSession(membershipTypeId, sessionId);
  }

  /**
   * Unlinks a session from a membership type.
   *
   * @param membershipTypeId the membership type ID
   * @param sessionId        the session ID
   * @return the updated membership type
   */
  @MutationMapping
  public MembershipType removeSessionFromMembership(@Argument final Long membershipTypeId,
      @Argument final Long sessionId) {
    return membershipTypeService.removeSession(membershipTypeId, sessionId);
  }
}
