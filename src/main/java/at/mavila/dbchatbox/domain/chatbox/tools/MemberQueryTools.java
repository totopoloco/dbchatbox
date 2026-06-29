package at.mavila.dbchatbox.domain.chatbox.tools;

import static java.util.Objects.nonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.member.KeycloakMemberService;
import at.mavila.dbchatbox.domain.club.member.MemberService;
import at.mavila.dbchatbox.domain.club.member.MemberStatusHistory;
import at.mavila.dbchatbox.domain.club.member.MemberView;
import at.mavila.dbchatbox.domain.club.member.Status;
import lombok.RequiredArgsConstructor;

/**
 * AI tools that read member data — listing, fetching by ID, status history.
 *
 * <p>
 * Each {@code @Tool} description is what the LLM reads when deciding whether
 * this method is relevant to the user's question. Keep them fact-dense and
 * avoid marketing prose. Bullet-list triggering questions in the description
 * if the match would otherwise be ambiguous.
 * </p>
 *
 * @since 2026-04-20
 */
@Component
@RequiredArgsConstructor
public class MemberQueryTools {

  private final KeycloakMemberService keycloakMemberService;
  private final MemberService memberService;

  /**
   * Lists club members. Use for questions like:
   * "show me all members", "which members are inactive", "who joined recently".
   */
  @Tool(description = """
      List all club members, optionally filtered by current status.
      Returns id, name, email, phone, the date the person became a member,
      the date their membership ends (or null if open-ended), and their
      current status (ACTIVE, INACTIVE, or DELETED).
      Use this for any question about who the members are or how many there are.
      """)
  public List<MemberSummary> listMembers(
      @ToolParam(required = false, description = """
          Optional status filter. One of ACTIVE, INACTIVE, DELETED.
          Leave null to list every member regardless of status.
          """) final String status) {

    final Status statusEnum = nonNull(status) && !status.isBlank() ? Status.valueOf(status) : null;
    return keycloakMemberService.findAll(statusEnum).stream()
        .map(this::toSummary)
        .toList();
  }

  /**
   * Looks up a single member by their ID. Use when the user asks about a
   * specific member and provides an identifier, or after another tool
   * returned an ID that needs enriched details.
   */
  @Tool(description = """
      Fetch the full profile of one member by their ID.
      Returns null if no member exists with that ID.
      """)
  public MemberSummary memberById(
      @ToolParam(description = "The member's TSID (the same id string returned by listMembers).")
      final Long id) {
    try {
      return toSummary(keycloakMemberService.findById(id));
    } catch (final MemberNotFoundException ex) {
      return null;
    }
  }

  /**
   * Returns the full status-change audit trail for a member. Useful for
   * questions like "when was Anna deactivated?" or "why did John's status change?".
   */
  @Tool(description = """
      Return the full status-change history of a single member, most recent first.
      Each entry has the status, the timestamp it was recorded, and an optional reason text.
      """)
  public List<StatusEntry> memberStatusHistory(
      @ToolParam(description = "The member's TSID.") final Long memberId) {
    return memberService.getStatusHistory(memberId).stream()
        .map(this::toStatusEntry)
        .toList();
  }

  // ---------------------------------------------------------------
  // entity → DTO mapping
  // ---------------------------------------------------------------

  private MemberSummary toSummary(final MemberView member) {
    return new MemberSummary(
        member.id(),
        member.firstName(),
        member.lastName(),
        member.email(),
        member.phoneNumber(),
        member.memberSince(),
        member.memberUntil(),
        keycloakMemberService.getCurrentStatus(member).name());
  }

  private StatusEntry toStatusEntry(final MemberStatusHistory history) {
    return new StatusEntry(
        history.getStatus().name(),
        history.getChangedAt(),
        history.getReason());
  }

  // ---------------------------------------------------------------
  // DTOs exposed to the LLM
  // ---------------------------------------------------------------

  /**
   * Flat view of a {@link MemberView} for LLM consumption.
   */
  public record MemberSummary(
      Long id,
      String firstName,
      String lastName,
      String email,
      String phoneNumber,
      LocalDate memberSince,
      LocalDate memberUntil,
      String currentStatus) {
  }

  /**
   * One entry in a member's status-change timeline.
   */
  public record StatusEntry(
      String status,
      LocalDateTime changedAt,
      String reason) {
  }
}
