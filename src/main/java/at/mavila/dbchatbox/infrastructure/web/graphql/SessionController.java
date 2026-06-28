package at.mavila.dbchatbox.infrastructure.web.graphql;

import static java.util.Objects.nonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.training.CreateOccurrencesCommand;
import at.mavila.dbchatbox.domain.club.training.CreateSessionCommand;
import at.mavila.dbchatbox.domain.club.training.OccurrenceFilter;
import at.mavila.dbchatbox.domain.club.training.Session;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrence;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceService;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceStatus;
import at.mavila.dbchatbox.domain.club.training.SessionService;
import at.mavila.dbchatbox.domain.club.training.SessionType;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for session and session occurrence queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SessionController {

  private final SessionService sessionService;
  private final SessionOccurrenceService occurrenceService;

  // ==================== QUERIES ====================

  /**
   * Returns all sessions, optionally filtered by type.
   *
   * @param sessionType optional filter (e.g. {@code TRAINING}, {@code FREE_GAME})
   * @return list of matching sessions
   */
  @QueryMapping
  public List<Session> sessions(@Argument final String sessionType) {
    final SessionType type = nonNull(sessionType) ? SessionType.valueOf(sessionType) : null;
    return sessionService.findAll(type);
  }

  /**
   * Returns session occurrences filtered by session, date range, and status.
   *
   * @param sessionId optional session ID filter
   * @param from      optional range start date
   * @param to        optional range end date
   * @param status    optional status filter
   * @return list of matching occurrences
   */
  @QueryMapping
  public List<SessionOccurrence> sessionOccurrences(@Argument final Long sessionId, @Argument final LocalDate from,
      @Argument final LocalDate to, @Argument final String status) {
    final SessionOccurrenceStatus statusEnum = nonNull(status) ? SessionOccurrenceStatus.valueOf(status) : null;
    return occurrenceService.findOccurrences(new OccurrenceFilter(sessionId, from, to, statusEnum));
  }

  /**
   * Returns sessions available to a member via their active subscriptions.
   *
   * @param memberId    the member ID
   * @param from        optional range start date
   * @param to          optional range end date
   * @param sessionType optional session type filter (unused in current implementation)
   * @return list of session occurrences
   */
  @QueryMapping
  public List<SessionOccurrence> sessionsByMember(@Argument final Long memberId, @Argument final LocalDate from,
      @Argument final LocalDate to, @Argument final String sessionType) {
    return occurrenceService.findByMember(memberId, from, to);
  }

  /**
   * Returns the next upcoming {@code SCHEDULED} occurrence for a member.
   *
   * @param memberId    the member ID
   * @param sessionType optional session type filter (unused in current implementation)
   * @return the next session occurrence, or {@code null} if none
   */
  @QueryMapping
  public SessionOccurrence nextSessionForMember(@Argument final Long memberId, @Argument final String sessionType) {
    return occurrenceService.findNextForMember(memberId);
  }

  /**
   * Returns sessions available to the authenticated member.
   * Phase 1: uses explicit {@code memberId} until authentication is implemented.
   *
   * @param memberId    the member ID
   * @param from        optional range start date
   * @param to          optional range end date
   * @param sessionType optional session type filter
   * @return list of session occurrences
   */
  @QueryMapping
  public List<SessionOccurrence> mySessions(@Argument final Long memberId, @Argument final LocalDate from,
      @Argument final LocalDate to, @Argument final String sessionType) {
    return occurrenceService.findByMember(memberId, from, to);
  }

  /**
   * Returns the next upcoming {@code SCHEDULED} occurrence for the authenticated member.
   * Phase 1: uses explicit {@code memberId} until authentication is implemented.
   *
   * @param memberId    the member ID
   * @param sessionType optional session type filter
   * @return the next session occurrence, or {@code null} if none
   */
  @QueryMapping
  public SessionOccurrence myNextSession(@Argument final Long memberId, @Argument final String sessionType) {
    return occurrenceService.findNextForMember(memberId);
  }

  /**
   * Returns trainers not assigned to any session overlapping the given day and time range.
   *
   * @param dayOfWeek the day of the week
   * @param startTime the start time
   * @param endTime   the end time
   * @return list of available trainers
   */
  @QueryMapping
  public List<Trainer> availableTrainers(@Argument final String dayOfWeek, @Argument final LocalTime startTime,
      @Argument final LocalTime endTime) {
    return sessionService.findAvailableTrainers(DayOfWeek.valueOf(dayOfWeek), startTime, endTime);
  }

  // ==================== MUTATIONS ====================

  /**
   * Creates a recurring session (training or free game).
   *
   * @param input the session creation input
   * @return the created session
   */
  @MutationMapping
  public Session createSession(@Argument final Map<String, Object> input) {
    final var command = new CreateSessionCommand((String) input.get("name"),
        SessionType.valueOf((String) input.get("sessionType")), DayOfWeek.valueOf((String) input.get("dayOfWeek")),
        (LocalTime) input.get("startTime"), (LocalTime) input.get("endTime"), (String) input.get("location"),
        input.containsKey("trainerId") ? Long.valueOf(input.get("trainerId").toString()) : null);
    return sessionService.createSession(command);
  }

  /**
   * Bulk-creates session occurrences for a date range, generating one per matching weekday.
   *
   * @param input the bulk creation input containing session ID, date range, and optional skip dates
   * @return list of newly created occurrences
   */
  @MutationMapping
  @SuppressWarnings("unchecked")
  public List<SessionOccurrence> createSessionOccurrences(@Argument final Map<String, Object> input) {

    final List<LocalDate> skipDates = input.containsKey("skipDates") ? (List<LocalDate>) input.get("skipDates") : null;

    final var command = new CreateOccurrencesCommand(Long.valueOf(input.get("sessionId").toString()),
        (LocalDate) input.get("startDate"), (LocalDate) input.get("endDate"), skipDates);
    return occurrenceService.createOccurrences(command);
  }

  /**
   * Cancels a specific session occurrence (sets status to {@code CANCELLED}).
   *
   * @param id the occurrence ID
   * @return the cancelled occurrence
   */
  @MutationMapping
  public SessionOccurrence cancelSessionOccurrence(@Argument final Long id) {
    return occurrenceService.cancel(id);
  }

  /**
   * Marks a session occurrence as completed (sets status to {@code COMPLETED}).
   *
   * @param id the occurrence ID
   * @return the completed occurrence
   */
  @MutationMapping
  public SessionOccurrence completeSessionOccurrence(@Argument final Long id) {
    return occurrenceService.complete(id);
  }
}
