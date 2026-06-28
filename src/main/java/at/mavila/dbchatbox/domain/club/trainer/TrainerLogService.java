package at.mavila.dbchatbox.domain.club.trainer;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrence;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceRepository;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceStatus;
import at.mavila.dbchatbox.domain.club.training.SessionType;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for trainer hour logging — submit, approve, reject, resubmit, and summaries.
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class TrainerLogService {

  private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(24);

  private final TrainerLogRepository trainerLogRepository;
  private final TrainerRepository trainerRepository;
  private final TrainerSettingsRepository trainerSettingsRepository;
  private final SessionOccurrenceRepository occurrenceRepository;
  private final CommandValidator commandValidator;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Admin directly logs hours for a trainer (bypasses approval — status set to APPROVED).
   *
   * @param command
   *                  the log-hours command
   * @return the created log entry with status APPROVED
   */
  public TrainerLog logTrainerHours(final LogTrainerHoursCommand command) {
    return createLogEntry(command, TrainerLogStatus.APPROVED, LocalDateTime.now());
  }

  /**
   * Trainer submits hours (PENDING or auto-approved per trainer setting).
   *
   * @param command
   *                  the log-hours command
   * @return the created log entry
   */
  public TrainerLog submitTrainerHours(final LogTrainerHoursCommand command) {
    final TrainerSettings settings = findSettings(command.trainerId());
    final boolean autoApprove = Boolean.TRUE.equals(settings.getAutoApproveHours());
    return createLogEntry(command, autoApprove ? TrainerLogStatus.APPROVED : TrainerLogStatus.PENDING,
        autoApprove ? LocalDateTime.now() : null);
  }

  /**
   * Approves a pending trainer log entry.
   *
   * @param id
   *             the log entry ID
   * @return the approved entry
   * @throws InvalidOperationException
   *                                     if the entry is not PENDING
   */
  public TrainerLog approveLog(final Long id) {
    return reviewLog(id, TrainerLogStatus.APPROVED, null);
  }

  /**
   * Rejects a pending trainer log entry with a reason.
   *
   * @param id
   *                 the log entry ID
   * @param reason
   *                 the rejection reason (required, not blank)
   * @return the rejected entry
   * @throws InvalidOperationException
   *                                     if the entry is not PENDING or reason is blank
   */
  public TrainerLog rejectLog(final Long id, final String reason) {
    if (isNull(reason) || reason.isBlank()) {
      throw new InvalidOperationException("Rejection reason is required");
    }
    return reviewLog(id, TrainerLogStatus.REJECTED, reason);
  }

  /**
   * Resubmits corrected hours after rejection.
   *
   * @param id
   *                      the log entry ID
   * @param hoursWorked
   *                      corrected hours
   * @param notes
   *                      updated notes
   * @return the resubmitted entry (status reset to PENDING or auto-approved)
   * @throws InvalidOperationException
   *                                     if the entry is not REJECTED
   */
  public TrainerLog resubmitLog(final Long id, final BigDecimal hoursWorked, final String notes) {
    final TrainerLog log = findLogOrThrow(id);
    if (log.getStatus() != TrainerLogStatus.REJECTED) {
      throw new InvalidOperationException("Can only resubmit REJECTED entries, current: %s".formatted(log.getStatus()));
    }
    if (hoursWorked.compareTo(BigDecimal.ZERO) <= 0 || hoursWorked.compareTo(MAX_HOURS) > 0) {
      throw new InvalidOperationException("Hours worked must be between 0 (exclusive) and 24 (inclusive)");
    }

    final TrainerSettings settings = findSettings(log.getTrainer().getId());
    final boolean autoApprove = Boolean.TRUE.equals(settings.getAutoApproveHours());
    final LocalDateTime now = LocalDateTime.now();

    log.setHoursWorked(hoursWorked);
    log.setNotes(notes);
    log.setRejectionReason(null);
    log.setStatus(autoApprove ? TrainerLogStatus.APPROVED : TrainerLogStatus.PENDING);
    log.setReviewedAt(autoApprove ? now : null);
    log.setSubmittedAt(now);

    return trainerLogRepository.save(log);
  }

  /**
   * Finds pending log entries, optionally filtered by trainer.
   *
   * @param trainerId
   *                    the trainer ID (null for all)
   * @return pending log entries
   */
  @Transactional(readOnly = true)
  public List<TrainerLog> findPendingLogs(final Long trainerId) {
    if (nonNull(trainerId)) {
      return trainerLogRepository.findByTrainerIdAndStatus(trainerId, TrainerLogStatus.PENDING);
    }
    return trainerLogRepository.findByStatus(TrainerLogStatus.PENDING);
  }

  /**
   * Calculates approved hours summary for a trainer in a date range.
   *
   * @param trainerId
   *                    the trainer ID
   * @param from
   *                    start date
   * @param to
   *                    end date
   * @return summary with total hours, session count, and total owed
   */
  @Transactional(readOnly = true)
  public TrainerHoursSummary getHoursSummary(final Long trainerId, final LocalDate from, final LocalDate to) {
    final Trainer trainer = findTrainer(trainerId);
    final TrainerSettings settings = findSettings(trainerId);
    final BigDecimal totalHours = trainerLogRepository.sumApprovedHoursByTrainerAndDateRange(trainerId, from, to);
    final long sessionCount = trainerLogRepository.countApprovedByTrainerAndDateRange(trainerId, from, to);
    final BigDecimal totalOwed = totalHours.multiply(settings.getHourlyRate());

    return new TrainerHoursSummary(trainer, totalHours, (int) sessionCount, totalOwed, from, to);
  }

  /**
   * Calculates full payment summary for a trainer in a date range.
   *
   * @param trainerId
   *                    the trainer ID
   * @param from
   *                    start date
   * @param to
   *                    end date
   * @return payment summary with approved and pending totals
   */
  @Transactional(readOnly = true)
  public TrainerPaymentSummary getPaymentSummary(final Long trainerId, final LocalDate from, final LocalDate to) {
    final Trainer trainer = findTrainer(trainerId);
    final TrainerSettings settings = findSettings(trainerId);
    final BigDecimal approvedHours = trainerLogRepository.sumApprovedHoursByTrainerAndDateRange(trainerId, from, to);
    final long approvedSessions = trainerLogRepository.countApprovedByTrainerAndDateRange(trainerId, from, to);
    final BigDecimal pendingHours = trainerLogRepository.sumPendingHoursByTrainerAndDateRange(trainerId, from, to);
    final long pendingSessions = trainerLogRepository.countPendingByTrainerAndDateRange(trainerId, from, to);
    final BigDecimal totalOwed = approvedHours.multiply(settings.getHourlyRate());

    return new TrainerPaymentSummary(trainer, from, to, approvedHours, (int) approvedSessions, settings.getHourlyRate(),
        totalOwed, settings.getPaymentMode().name(), pendingHours, (int) pendingSessions);
  }

  private Trainer findTrainer(final Long trainerId) {
    return tenantScopedFinder.findById(trainerRepository, trainerId)
        .orElseThrow(() -> new ResourceNotFoundException("Trainer", trainerId));
  }

  private TrainerLog createLogEntry(final LogTrainerHoursCommand command, final TrainerLogStatus status,
      final LocalDateTime reviewedAt) {
    commandValidator.validate(command);

    final Trainer trainer = findTrainer(command.trainerId());
    final SessionOccurrence occurrence = findOccurrence(command.sessionOccurrenceId());

    validateOccurrenceForLogging(occurrence, trainer);
    validateNoDuplicate(command.trainerId(), command.sessionOccurrenceId());

    final TrainerLog log = TrainerLog.builder().trainer(trainer).sessionOccurrence(occurrence)
        .hoursWorked(command.hoursWorked()).status(status).submittedAt(LocalDateTime.now()).reviewedAt(reviewedAt)
        .notes(command.notes()).build();

    return trainerLogRepository.save(log);
  }

  private TrainerLog reviewLog(final Long id, final TrainerLogStatus targetStatus, final String rejectionReason) {
    final TrainerLog log = findLogOrThrow(id);
    if (log.getStatus() != TrainerLogStatus.PENDING) {
      throw new InvalidOperationException("Can only %s PENDING entries, current: %s"
          .formatted(targetStatus == TrainerLogStatus.APPROVED ? "approve" : "reject", log.getStatus()));
    }
    log.setStatus(targetStatus);
    log.setReviewedAt(LocalDateTime.now());
    if (nonNull(rejectionReason)) {
      log.setRejectionReason(rejectionReason);
    }
    return trainerLogRepository.save(log);
  }

  private TrainerSettings findSettings(final Long trainerId) {
    return trainerSettingsRepository.findByTrainerId(trainerId)
        .orElseThrow(() -> new ResourceNotFoundException("TrainerSettings", trainerId));
  }

  private SessionOccurrence findOccurrence(final Long occurrenceId) {
    return tenantScopedFinder.findById(occurrenceRepository, occurrenceId)
        .orElseThrow(() -> new ResourceNotFoundException("SessionOccurrence", occurrenceId));
  }

  private TrainerLog findLogOrThrow(final Long id) {
    return tenantScopedFinder.findById(trainerLogRepository, id)
        .orElseThrow(() -> new ResourceNotFoundException("TrainerLog", id));
  }

  private void validateOccurrenceForLogging(final SessionOccurrence occurrence, final Trainer trainer) {
    if (occurrence.getStatus() != SessionOccurrenceStatus.COMPLETED) {
      throw new InvalidOperationException(
          "Can only log hours for COMPLETED occurrences, current: %s".formatted(occurrence.getStatus()));
    }
    if (occurrence.getSession().getSessionType() != SessionType.TRAINING) {
      throw new InvalidOperationException("Can only log hours for TRAINING sessions");
    }
    if (!isTrainerAssigned(occurrence, trainer)) {
      throw new InvalidOperationException("Trainer is not assigned to this session");
    }
  }

  private boolean isTrainerAssigned(final SessionOccurrence occurrence, final Trainer trainer) {
    return nonNull(occurrence.getSession().getTrainer())
        && occurrence.getSession().getTrainer().getId().equals(trainer.getId());
  }

  private void validateNoDuplicate(final Long trainerId, final Long sessionOccurrenceId) {
    if (trainerLogRepository.existsByTrainerIdAndSessionOccurrenceId(trainerId, sessionOccurrenceId)) {
      throw new InvalidOperationException("A log entry already exists for this trainer and session occurrence");
    }
  }

  /**
   * Summary of approved hours for a trainer.
   */
  public record TrainerHoursSummary(Trainer trainer, BigDecimal totalHours, int sessionCount, BigDecimal totalOwed,
      LocalDate from, LocalDate to) {
  }

  /**
   * Full payment summary for a trainer including pending and approved totals.
   */
  public record TrainerPaymentSummary(Trainer trainer, LocalDate from, LocalDate to, BigDecimal approvedHours,
      int approvedSessions, BigDecimal hourlyRate, BigDecimal totalOwed, String paymentMode, BigDecimal pendingHours,
      int pendingSessions) {
  }
}
