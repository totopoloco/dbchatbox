package at.mavila.dbchatbox.domain.chatbox.tools;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.training.Session;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrence;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceService;
import at.mavila.dbchatbox.domain.club.training.SessionService;
import at.mavila.dbchatbox.domain.club.training.SessionType;
import lombok.RequiredArgsConstructor;

/**
 * AI tools that read session and session-occurrence data (the weekly
 * schedule, and the concrete dated instances of each recurring slot).
 *
 * @since 2026-04-20
 */
@Component
@RequiredArgsConstructor
public class SessionQueryTools {

  private final SessionService sessionService;
  private final SessionOccurrenceService occurrenceService;

  /**
   * Lists all recurring session templates (the weekly schedule).
   */
  @Tool(description = """
      List every recurring session the club runs (the weekly schedule).
      A session has a name, a session type (TRAINING or FREE_GAME), a weekday,
      a start/end time, a location, and an optional assigned trainer.
      Use this for "what sessions do we have on Tuesdays?", "what training slots exist?".
      """)
  public List<SessionSummary> listSessions(
      @ToolParam(required = false, description = """
          Optional session-type filter. One of TRAINING, FREE_GAME.
          Leave null to list every session.
          """) final String sessionType) {

    final SessionType typeEnum =
        nonNull(sessionType) && !sessionType.isBlank() ? SessionType.valueOf(sessionType) : null;

    return sessionService.findAll(typeEnum).stream()
        .map(this::toSummary)
        .toList();
  }

  /**
   * Session occurrences for a specific member in a date range (their
   * personal schedule).
   */
  @Tool(description = """
      List the session occurrences available to a specific member within an optional date range.
      An occurrence is one concrete dated instance of a recurring session
      (e.g. "Badminton training on 2026-04-21"). The member's active subscriptions determine
      which sessions they may attend.
      Use this for "show me Anna's schedule next week", "what sessions can John attend?".
      """)
  public List<SessionOccurrenceSummary> sessionsForMember(
      @ToolParam(description = "The member's TSID.") final Long memberId,
      @ToolParam(required = false, description = "Inclusive start date in ISO format (YYYY-MM-DD). Null = no lower bound.")
      final LocalDate from,
      @ToolParam(required = false, description = "Inclusive end date in ISO format (YYYY-MM-DD). Null = no upper bound.")
      final LocalDate to) {

    return occurrenceService.findByMember(memberId, from, to).stream()
        .map(this::toOccurrenceSummary)
        .toList();
  }

  /**
   * The next upcoming SCHEDULED occurrence a member can attend.
   */
  @Tool(description = """
      Return the single next upcoming SCHEDULED session occurrence available to a member,
      or null if they have nothing on the calendar. Returns only future occurrences (today onward).
      Use this for "when is Anna's next training?", "what is John's next session?".
      """)
  public SessionOccurrenceSummary nextSessionForMember(
      @ToolParam(description = "The member's TSID.") final Long memberId) {
    final SessionOccurrence occurrence = occurrenceService.findNextForMember(memberId);
    return isNull(occurrence) ? null : toOccurrenceSummary(occurrence);
  }

  // ---------------------------------------------------------------
  // mapping
  // ---------------------------------------------------------------

  private SessionSummary toSummary(final Session session) {
    final Trainer trainer = session.getTrainer();
    return new SessionSummary(
        session.getId(),
        session.getName(),
        session.getSessionType().name(),
        session.getDayOfWeek().name(),
        session.getStartTime(),
        session.getEndTime(),
        session.getLocation(),
        isNull(trainer) ? null : "%s %s".formatted(trainer.getFirstName(), trainer.getLastName()));
  }

  private SessionOccurrenceSummary toOccurrenceSummary(final SessionOccurrence occurrence) {
    final Session session = occurrence.getSession();
    final Trainer trainer = nonNull(session) ? session.getTrainer() : null;

    return new SessionOccurrenceSummary(
        occurrence.getId(),
        isNull(session) ? null : session.getId(),
        isNull(session) ? null : session.getName(),
        isNull(session) ? null : session.getSessionType().name(),
        occurrence.getDate(),
        isNull(session) ? null : session.getStartTime(),
        isNull(session) ? null : session.getEndTime(),
        isNull(session) ? null : session.getLocation(),
        isNull(trainer) ? null : "%s %s".formatted(trainer.getFirstName(), trainer.getLastName()),
        occurrence.getStatus().name(),
        occurrence.getNotes());
  }

  // ---------------------------------------------------------------
  // DTOs
  // ---------------------------------------------------------------

  /**
   * Flat view of a recurring {@link Session}.
   */
  public record SessionSummary(
      Long id,
      String name,
      String sessionType,
      String dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      String location,
      String trainerName) {
  }

  /**
   * Flat view of a {@link SessionOccurrence} — merged with its parent {@link Session}
   * so the LLM does not need to navigate a nested object graph.
   */
  public record SessionOccurrenceSummary(
      Long id,
      Long sessionId,
      String sessionName,
      String sessionType,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      String location,
      String trainerName,
      String status,
      String notes) {
  }
}
