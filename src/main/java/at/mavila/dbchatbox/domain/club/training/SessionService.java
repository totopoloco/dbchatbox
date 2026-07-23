package at.mavila.dbchatbox.domain.club.training;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.OverlapException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.trainer.TrainerRepository;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for session management — creation, overlap validation, trainer availability.
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class SessionService {

  private final SessionRepository sessionRepository;
  private final TrainerRepository trainerRepository;
  private final CommandValidator commandValidator;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Creates a new recurring session.
   *
   * @param command
   *                  the session creation command
   * @return the created session
   * @throws InvalidOperationException
   *                                     if time ordering is invalid or trainer/type mismatch
   * @throws OverlapException
   *                                     if trainer or location overlap is detected
   */
  public Session createSession(final CreateSessionCommand command) {
    commandValidator.validate(command);

    Trainer trainer = null;
    if (nonNull(command.trainerId())) {
      trainer = tenantScopedFinder.findById(trainerRepository, command.trainerId())
          .orElseThrow(() -> new ResourceNotFoundException("Trainer", command.trainerId()));
      checkOverlap(sessionRepository.findByTrainerIdAndDayOfWeek(command.trainerId(), command.dayOfWeek()),
          command.startTime(), command.endTime(), null,
          "Trainer already has a session on %s with overlapping time".formatted(command.dayOfWeek()));
    }

    checkOverlap(sessionRepository.findByLocationAndDayOfWeek(command.location(), command.dayOfWeek()),
        command.startTime(), command.endTime(), null, "Location '%s' already has a session on %s with overlapping time"
            .formatted(command.location(), command.dayOfWeek()));

    final Session session = Session.builder().name(command.name()).sessionType(command.sessionType())
        .dayOfWeek(command.dayOfWeek()).startTime(command.startTime()).endTime(command.endTime())
        .location(command.location()).trainer(trainer).build();

    return sessionRepository.save(session);
  }

  /**
   * Lists all sessions, optionally filtered by type.
   *
   * @param sessionType
   *                      the type filter (null for all)
   * @return matching sessions
   */
  @Transactional(readOnly = true)
  public List<Session> findAll(final SessionType sessionType) {
    final Long tenantId = currentTenantOrThrow();
    if (nonNull(sessionType)) {
      return sessionRepository.findBySessionTypeAndTenantId(sessionType, tenantId);
    }
    return sessionRepository.findAllByTenantId(tenantId);
  }

  /**
   * Finds a session by ID.
   *
   * @param id
   *             the session ID
   * @return the session
   * @throws ResourceNotFoundException
   *                                     if not found
   */
  @Transactional(readOnly = true)
  public Session findById(final Long id) {
    return tenantScopedFinder.findById(sessionRepository, id)
        .orElseThrow(() -> new ResourceNotFoundException("Session", id));
  }

  /**
   * Finds trainers not assigned to any session overlapping the given day + time range.
   *
   * @param dayOfWeek
   *                    the day of the week
   * @param startTime
   *                    the start time
   * @param endTime
   *                    the end time
   * @return available trainers
   */
  @Transactional(readOnly = true)
  public List<Trainer> findAvailableTrainers(final DayOfWeek dayOfWeek, final LocalTime startTime,
      final LocalTime endTime) {
    final java.util.Set<Long> busyTrainerIds =
        sessionRepository.findBusyTrainerIdsForSlot(dayOfWeek, startTime, endTime);
    return trainerRepository.findAllByTenantId(currentTenantOrThrow()).stream()
        .filter(trainer -> !busyTrainerIds.contains(trainer.getId()))
        .toList();
  }

  private void checkOverlap(final List<Session> existing, final LocalTime startTime, final LocalTime endTime,
      final Long excludeSessionId, final String errorMessage) {
    if (hasOverlap(existing, startTime, endTime, excludeSessionId)) {
      throw new OverlapException(errorMessage);
    }
  }

  private boolean hasOverlap(final List<Session> sessions, final LocalTime startTime, final LocalTime endTime,
      final Long excludeSessionId) {
    return sessions.stream().filter(s -> isNull(excludeSessionId) || !s.getId().equals(excludeSessionId))
        .anyMatch(s -> timesOverlap(startTime, endTime, s.getStartTime(), s.getEndTime()));
  }

  private boolean timesOverlap(final LocalTime start1, final LocalTime end1, final LocalTime start2,
      final LocalTime end2) {
    return start1.isBefore(end2) && start2.isBefore(end1);
  }

  private Long currentTenantOrThrow() {
    final Long t = TenantContext.getTenantId();
    if (isNull(t)) {
      throw new IllegalStateException("No tenant in context");
    }
    return t;
  }
}
