package at.mavila.dbchatbox.domain.club.training;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for session occurrence management — bulk creation, status transitions, member queries.
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class SessionOccurrenceService {

  private final SessionOccurrenceRepository occurrenceRepository;
  private final SessionRepository sessionRepository;

  /**
   * Bulk-creates session occurrences for a date range, one per matching weekday.
   *
   * <p>
   * Idempotent: skips dates that already have an occurrence. Skips dates in the skipDates list.
   * </p>
   *
   * @param sessionId
   *                    the session ID
   * @param startDate
   *                    the start date (inclusive)
   * @param endDate
   *                    the end date (inclusive)
   * @param skipDates
   *                    dates to exclude (optional)
   * @return newly created occurrences
   * @throws ResourceNotFoundException
   *                                     if the session does not exist
   */
  public List<SessionOccurrence> createOccurrences(final Long sessionId, final LocalDate startDate,
      final LocalDate endDate, final List<LocalDate> skipDates) {
    final Session session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

    final DayOfWeek targetDay = session.getDayOfWeek();
    final Set<LocalDate> skip = isNull(skipDates) ? Set.of() : new HashSet<>(skipDates);
    final List<SessionOccurrence> created = new ArrayList<>();

    LocalDate current = startDate;
    while (!current.isAfter(endDate)) {
      if (current.getDayOfWeek() == targetDay && !skip.contains(current)
          && !occurrenceRepository.existsBySessionIdAndDate(sessionId, current)) {
        final SessionOccurrence occurrence = SessionOccurrence.builder().session(session).date(current)
            .status(SessionOccurrenceStatus.SCHEDULED).build();
        created.add(occurrenceRepository.save(occurrence));
      }
      current = current.plusDays(1);
    }

    return created;
  }

  /**
   * Cancels a session occurrence.
   *
   * @param id
   *             the occurrence ID
   * @return the cancelled occurrence
   * @throws ResourceNotFoundException
   *                                     if the occurrence does not exist
   * @throws InvalidOperationException
   *                                     if the occurrence is not SCHEDULED
   */
  public SessionOccurrence cancel(final Long id) {
    final SessionOccurrence occurrence = findByIdOrThrow(id);
    validateScheduled(occurrence, "cancel");
    occurrence.setStatus(SessionOccurrenceStatus.CANCELLED);
    return occurrenceRepository.save(occurrence);
  }

  /**
   * Marks a session occurrence as completed.
   *
   * @param id
   *             the occurrence ID
   * @return the completed occurrence
   * @throws ResourceNotFoundException
   *                                     if the occurrence does not exist
   * @throws InvalidOperationException
   *                                     if the occurrence is not SCHEDULED
   */
  public SessionOccurrence complete(final Long id) {
    final SessionOccurrence occurrence = findByIdOrThrow(id);
    validateScheduled(occurrence, "complete");
    occurrence.setStatus(SessionOccurrenceStatus.COMPLETED);
    return occurrenceRepository.save(occurrence);
  }

  /**
   * Finds occurrences, optionally filtered by session, date range, and status.
   *
   * @param sessionId
   *                    the session ID (optional)
   * @param from
   *                    start date (optional)
   * @param to
   *                    end date (optional)
   * @param status
   *                    the status filter (optional)
   * @return matching occurrences
   */
  @Transactional(readOnly = true)
  public List<SessionOccurrence> findOccurrences(final Long sessionId, final LocalDate from, final LocalDate to,
      final SessionOccurrenceStatus status) {
    if (nonNull(sessionId) && nonNull(from) && nonNull(to) && nonNull(status)) {
      return occurrenceRepository.findBySessionIdAndDateBetweenAndStatus(sessionId, from, to, status);
    }
    if (nonNull(sessionId) && nonNull(from) && nonNull(to)) {
      return occurrenceRepository.findBySessionIdAndDateBetween(sessionId, from, to);
    }
    return occurrenceRepository.findAll();
  }

  /**
   * Finds occurrences available to a member through their active subscriptions.
   *
   * @param memberId
   *                   the member ID
   * @param from
   *                   start date
   * @param to
   *                   end date
   * @return matching occurrences
   */
  @Transactional(readOnly = true)
  public List<SessionOccurrence> findByMember(final Long memberId, final LocalDate from, final LocalDate to) {
    return occurrenceRepository.findByMemberAndDateRange(memberId, from, to);
  }

  /**
   * Finds the next upcoming SCHEDULED occurrence for a member.
   *
   * @param memberId
   *                   the member ID
   * @return the next occurrence, or null
   */
  @Transactional(readOnly = true)
  public SessionOccurrence findNextForMember(final Long memberId) {
    return occurrenceRepository.findNextForMember(memberId, LocalDate.now()).orElse(null);
  }

  private SessionOccurrence findByIdOrThrow(final Long id) {
    return occurrenceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SessionOccurrence", id));
  }

  private void validateScheduled(final SessionOccurrence occurrence, final String action) {
    if (occurrence.getStatus() != SessionOccurrenceStatus.SCHEDULED) {
      throw new InvalidOperationException(
          "Cannot %s occurrence with status %s (must be SCHEDULED)".formatted(action, occurrence.getStatus()));
    }
  }
}
