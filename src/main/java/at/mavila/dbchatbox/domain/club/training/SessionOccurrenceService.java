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
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
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
  private final CommandValidator commandValidator;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Bulk-creates session occurrences for a date range, one per matching weekday.
   *
   * <p>
   * Idempotent: skips dates that already have an occurrence. Skips dates in the skipDates list.
   * </p>
   *
   * @param command
   *                  the creation command
   * @return newly created occurrences
   * @throws ResourceNotFoundException
   *                                     if the session does not exist
   */
  public List<SessionOccurrence> createOccurrences(final CreateOccurrencesCommand command) {
    commandValidator.validate(command);

    final Session session = tenantScopedFinder.findById(sessionRepository, command.sessionId())
        .orElseThrow(() -> new ResourceNotFoundException("Session", command.sessionId()));

    final DayOfWeek targetDay = session.getDayOfWeek();
    final Set<LocalDate> skip = isNull(command.skipDates()) ? Set.of() : new HashSet<>(command.skipDates());
    final List<SessionOccurrence> created = new ArrayList<>();

    LocalDate current = command.startDate();
    while (!current.isAfter(command.endDate())) {
      if (shouldCreateOccurrence(current, targetDay, skip, command.sessionId())) {
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
   * @param filter
   *                 the occurrence filter (all fields optional)
   * @return matching occurrences
   */
  @Transactional(readOnly = true)
  public List<SessionOccurrence> findOccurrences(final OccurrenceFilter filter) {
    final boolean hasSession = nonNull(filter.sessionId());
    final boolean hasDateRange = nonNull(filter.from()) && nonNull(filter.to());
    final boolean hasStatus = nonNull(filter.status());

    if (hasSession && hasDateRange && hasStatus) {
      return occurrenceRepository.findBySessionIdAndDateBetweenAndStatus(filter.sessionId(), filter.from(), filter.to(),
          filter.status());
    }
    if (hasSession && hasDateRange) {
      return occurrenceRepository.findBySessionIdAndDateBetween(filter.sessionId(), filter.from(), filter.to());
    }
    return occurrenceRepository.findAllByTenantId(currentTenantOrThrow());
  }

  /**
   * Finds occurrences available to a member through their active subscriptions.
   *
   * <p>
   * {@code from} and {@code to} are <strong>optional</strong>: callers (the
   * GraphQL controllers and the chatbox tool) treat null as "no lower / upper
   * bound". The underlying JPQL uses {@code BETWEEN :from AND :to} which
   * cannot bind null parameters, so we substitute wide sentinel dates here:
   * {@code 1970-01-01} for an unbounded lower end and {@code 9999-12-31} for
   * an unbounded upper end. Both fit comfortably inside the standard SQL
   * {@code DATE} range supported by H2 and PostgreSQL.
   * </p>
   *
   * @param memberId the member ID
   * @param from     start date (inclusive); null means no lower bound
   * @param to       end date (inclusive); null means no upper bound
   * @return matching occurrences
   */
  @Transactional(readOnly = true)
  public List<SessionOccurrence> findByMember(final Long memberId, final LocalDate from, final LocalDate to) {
    final LocalDate effectiveFrom = nonNull(from) ? from : LocalDate.of(1970, 1, 1);
    final LocalDate effectiveTo = nonNull(to) ? to : LocalDate.of(9999, 12, 31);
    return occurrenceRepository.findByMemberAndDateRange(memberId, effectiveFrom, effectiveTo);
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
    return tenantScopedFinder.findById(occurrenceRepository, id)
        .orElseThrow(() -> new ResourceNotFoundException("SessionOccurrence", id));
  }

  private void validateScheduled(final SessionOccurrence occurrence, final String action) {
    if (occurrence.getStatus() != SessionOccurrenceStatus.SCHEDULED) {
      throw new InvalidOperationException(
          "Cannot %s occurrence with status %s (must be SCHEDULED)".formatted(action, occurrence.getStatus()));
    }
  }

  private boolean shouldCreateOccurrence(final LocalDate date, final DayOfWeek targetDay, final Set<LocalDate> skip,
      final Long sessionId) {
    return date.getDayOfWeek() == targetDay && !skip.contains(date)
        && !occurrenceRepository.existsBySessionIdAndDate(sessionId, date);
  }

  private Long currentTenantOrThrow() {
    final Long t = TenantContext.getTenantId();
    if (isNull(t)) {
      throw new IllegalStateException("No tenant in context");
    }
    return t;
  }
}
