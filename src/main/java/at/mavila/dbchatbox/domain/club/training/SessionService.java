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

  /**
   * Creates a new recurring session.
   *
   * @param name
   *                      the session name
   * @param sessionType
   *                      the session type (TRAINING or FREE_GAME)
   * @param dayOfWeek
   *                      the day of the week
   * @param startTime
   *                      the start time
   * @param endTime
   *                      the end time (must be after startTime)
   * @param location
   *                      the location
   * @param trainerId
   *                      the trainer ID (required for TRAINING, must be null for FREE_GAME)
   * @return the created session
   * @throws InvalidOperationException
   *                                     if time ordering is invalid or trainer/type mismatch
   * @throws OverlapException
   *                                     if trainer or location overlap is detected
   */
  public Session createSession(final String name, final SessionType sessionType, final DayOfWeek dayOfWeek,
      final LocalTime startTime, final LocalTime endTime, final String location, final Long trainerId) {
    validateTimeOrdering(startTime, endTime);
    validateTrainerRequirement(sessionType, trainerId);

    Trainer trainer = null;
    if (nonNull(trainerId)) {
      trainer = trainerRepository.findById(trainerId)
          .orElseThrow(() -> new ResourceNotFoundException("Trainer", trainerId));
      checkTrainerOverlap(trainerId, dayOfWeek, startTime, endTime, null);
    }

    checkLocationOverlap(location, dayOfWeek, startTime, endTime, null);

    final Session session = Session.builder().name(name).sessionType(sessionType).dayOfWeek(dayOfWeek)
        .startTime(startTime).endTime(endTime).location(location).trainer(trainer).build();

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
    if (nonNull(sessionType)) {
      return sessionRepository.findBySessionType(sessionType);
    }
    return sessionRepository.findAll();
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
    return sessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Session", id));
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
    return trainerRepository.findAll().stream()
        .filter(trainer -> !hasOverlappingSession(trainer.getId(), dayOfWeek, startTime, endTime)).toList();
  }

  private void validateTimeOrdering(final LocalTime startTime, final LocalTime endTime) {
    if (!endTime.isAfter(startTime)) {
      throw new InvalidOperationException("endTime must be after startTime");
    }
  }

  private void validateTrainerRequirement(final SessionType type, final Long trainerId) {
    if (type == SessionType.TRAINING && isNull(trainerId)) {
      throw new InvalidOperationException("TRAINING sessions require a trainerId");
    }
    if (type == SessionType.FREE_GAME && nonNull(trainerId)) {
      throw new InvalidOperationException("FREE_GAME sessions must not have a trainerId");
    }
  }

  private void checkTrainerOverlap(final Long trainerId, final DayOfWeek dayOfWeek, final LocalTime startTime,
      final LocalTime endTime, final Long excludeSessionId) {
    final List<Session> existing = sessionRepository.findByTrainerIdAndDayOfWeek(trainerId, dayOfWeek);
    final boolean hasOverlap = existing.stream()
        .filter(s -> isNull(excludeSessionId) || !s.getId().equals(excludeSessionId))
        .anyMatch(s -> timesOverlap(startTime, endTime, s.getStartTime(), s.getEndTime()));

    if (hasOverlap) {
      throw new OverlapException("Trainer already has a session on %s with overlapping time".formatted(dayOfWeek));
    }
  }

  private void checkLocationOverlap(final String location, final DayOfWeek dayOfWeek, final LocalTime startTime,
      final LocalTime endTime, final Long excludeSessionId) {
    final List<Session> existing = sessionRepository.findByLocationAndDayOfWeek(location, dayOfWeek);
    final boolean hasOverlap = existing.stream()
        .filter(s -> isNull(excludeSessionId) || !s.getId().equals(excludeSessionId))
        .anyMatch(s -> timesOverlap(startTime, endTime, s.getStartTime(), s.getEndTime()));

    if (hasOverlap) {
      throw new OverlapException(
          "Location '%s' already has a session on %s with overlapping time".formatted(location, dayOfWeek));
    }
  }

  private boolean hasOverlappingSession(final Long trainerId, final DayOfWeek dayOfWeek, final LocalTime startTime,
      final LocalTime endTime) {
    return sessionRepository.findByTrainerIdAndDayOfWeek(trainerId, dayOfWeek).stream()
        .anyMatch(s -> timesOverlap(startTime, endTime, s.getStartTime(), s.getEndTime()));
  }

  private boolean timesOverlap(final LocalTime start1, final LocalTime end1, final LocalTime start2,
      final LocalTime end2) {
    return start1.isBefore(end2) && start2.isBefore(end1);
  }
}
