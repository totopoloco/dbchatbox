package at.mavila.dbchatbox.domain.chatbox.tools;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeService;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeStatus;
import lombok.RequiredArgsConstructor;

/**
 * AI tools that read membership-type data (the catalogue of subscriptions the
 * club offers — "Gold Monthly", "Training Only", etc.).
 *
 * @since 2026-04-20
 */
@Component
@RequiredArgsConstructor
public class MembershipQueryTools {

  private final MembershipTypeService membershipTypeService;

  /**
   * Lists membership types (the catalogue of plans the club offers). Use for
   * "what memberships do we offer?", "how much is the gold plan?", etc.
   */
  @Tool(description = """
      List the membership types the club offers, optionally filtered by status.
      A membership type has a name, price, duration, unit (DAYS, WEEKS, MONTHS, YEARS),
      status (DRAFT, ACTIVE, INACTIVE), a flag indicating whether the price is prorated
      for mid-period joins, and a grace period (in days) after which an unpaid subscription
      is considered overdue.
      """)
  public List<MembershipTypeSummary> listMembershipTypes(
      @ToolParam(required = false, description = """
          Optional status filter. One of DRAFT, ACTIVE, INACTIVE.
          Leave null to list every membership type.
          """) final String status) {

    final MembershipTypeStatus statusEnum =
        nonNull(status) && !status.isBlank() ? MembershipTypeStatus.valueOf(status) : null;

    return membershipTypeService.findAll(statusEnum).stream()
        .map(this::toSummary)
        .toList();
  }

  private MembershipTypeSummary toSummary(final MembershipType type) {
    return new MembershipTypeSummary(
        type.getId(),
        type.getName(),
        type.getDescription(),
        type.getPrice(),
        type.getDuration(),
        type.getUnit().name(),
        type.getStatus().name(),
        type.getProratedMode(),
        type.getGracePeriodDays());
  }

  /**
   * Flat view of a {@link MembershipType} for LLM consumption.
   */
  public record MembershipTypeSummary(
      Long id,
      String name,
      String description,
      BigDecimal price,
      Integer duration,
      String unit,
      String status,
      Boolean proratedMode,
      Integer gracePeriodDays) {
  }
}
