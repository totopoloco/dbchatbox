package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.trainer.CreateTrainerCommand;
import at.mavila.dbchatbox.domain.club.trainer.LogTrainerHoursCommand;
import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLog;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLogService;
import at.mavila.dbchatbox.domain.club.trainer.TrainerPaymentMode;
import at.mavila.dbchatbox.domain.club.trainer.TrainerService;
import at.mavila.dbchatbox.domain.club.trainer.TrainerSettings;
import at.mavila.dbchatbox.domain.club.trainer.UpdateTrainerCommand;
import at.mavila.dbchatbox.domain.club.trainer.UpdateTrainerSettingsCommand;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for trainer queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
public class TrainerController {

  private final TrainerService trainerService;
  private final TrainerLogService trainerLogService;

  // ==================== QUERIES ====================

  /**
   * Returns all trainers.
   *
   * @return list of all trainers
   */
  @QueryMapping
  public List<Trainer> trainers() {
    return trainerService.findAll();
  }

  /**
   * Returns a summary of approved hours worked by a trainer in a date range.
   *
   * @param trainerId the trainer ID
   * @param from      range start date
   * @param to        range end date
   * @return hours summary including total hours, session count, and amount owed
   */
  @QueryMapping
  public Map<String, Object> trainerHours(@Argument final Long trainerId, @Argument final LocalDate from,
      @Argument final LocalDate to) {
    final var summary = trainerLogService.getHoursSummary(trainerId, from, to);
    return Map.of("trainer", summary.trainer(), "totalHours", summary.totalHours(), "sessionCount",
        summary.sessionCount(), "totalOwed", summary.totalOwed(), "from", summary.from(), "to", summary.to());
  }

  /**
   * Returns pending trainer log entries awaiting admin approval.
   *
   * @param trainerId optional trainer ID filter; if null, returns all pending
   *                  logs
   * @return list of pending trainer logs
   */
  @QueryMapping
  public List<TrainerLog> pendingTrainerLogs(@Argument final Long trainerId) {
    return trainerLogService.findPendingLogs(trainerId);
  }

  /**
   * Returns a payment summary for a trainer including approved and pending hours.
   *
   * @param trainerId the trainer ID
   * @param from      range start date
   * @param to        range end date
   * @return payment summary with hourly rate, total owed, and breakdown
   */
  @QueryMapping
  public Map<String, Object> trainerPaymentSummary(@Argument final Long trainerId, @Argument final LocalDate from,
      @Argument final LocalDate to) {
    final var summary = trainerLogService.getPaymentSummary(trainerId, from, to);
    return Map.of("trainer", summary.trainer(), "from", summary.from(), "to", summary.to(), "approvedHours",
        summary.approvedHours(), "approvedSessions", summary.approvedSessions(), "hourlyRate", summary.hourlyRate(),
        "totalOwed", summary.totalOwed(), "paymentMode", summary.paymentMode(), "pendingHours", summary.pendingHours(),
        "pendingSessions", summary.pendingSessions());
  }

  /**
   * Returns a payment summary for the authenticated trainer.
   * Phase 1: uses explicit {@code trainerId} until authentication is implemented.
   *
   * @param trainerId the trainer ID
   * @param from      range start date
   * @param to        range end date
   * @return payment summary with hourly rate, total owed, and breakdown
   */
  @QueryMapping
  public Map<String, Object> myTrainerPaymentSummary(@Argument final Long trainerId, @Argument final LocalDate from,
      @Argument final LocalDate to) {
    final var summary = trainerLogService.getPaymentSummary(trainerId, from, to);
    return Map.of("trainer", summary.trainer(), "from", summary.from(), "to", summary.to(), "approvedHours",
        summary.approvedHours(), "approvedSessions", summary.approvedSessions(), "hourlyRate", summary.hourlyRate(),
        "totalOwed", summary.totalOwed(), "paymentMode", summary.paymentMode(), "pendingHours", summary.pendingHours(),
        "pendingSessions", summary.pendingSessions());
  }

  // ==================== FIELD RESOLVERS ====================

  /**
   * Resolves the settings for a trainer.
   *
   * @param trainer the trainer
   * @return the trainer's compensation and workflow settings
   */
  @SchemaMapping(typeName = "Trainer", field = "settings")
  public TrainerSettings trainerSettings(final Trainer trainer) {
    return trainerService.findSettingsByTrainerId(trainer.getId());
  }

  // ==================== MUTATIONS ====================

  /**
   * Registers a new trainer and creates initial compensation settings.
   *
   * @param input the trainer creation input
   * @return the created trainer
   */
  @MutationMapping
  public Trainer createTrainer(@Argument final Map<String, Object> input) {
    final var command = new CreateTrainerCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"),
        new BigDecimal(input.get("hourlyRate").toString()),
        TrainerPaymentMode.valueOf((String) input.get("paymentMode")),
        input.containsKey("autoApproveHours") ? (Boolean) input.get("autoApproveHours") : null);
    return trainerService.createTrainer(command);
  }

  /**
   * Updates a trainer's contact details.
   *
   * @param id    the trainer ID
   * @param input the update input
   * @return the updated trainer
   */
  @MutationMapping
  public Trainer updateTrainer(@Argument final Long id, @Argument final Map<String, Object> input) {
    final var command = new UpdateTrainerCommand((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"));
    return trainerService.updateTrainer(id, command);
  }

  /**
   * Updates a trainer's compensation and workflow settings (admin-only).
   *
   * @param trainerId the trainer ID
   * @param input     the settings update input
   * @return the updated trainer settings
   */
  @MutationMapping
  public TrainerSettings updateTrainerSettings(@Argument final Long trainerId,
      @Argument final Map<String, Object> input) {
    final var command = new UpdateTrainerSettingsCommand(
        input.containsKey("hourlyRate") ? new BigDecimal(input.get("hourlyRate").toString()) : null,
        input.containsKey("paymentMode") ? TrainerPaymentMode.valueOf((String) input.get("paymentMode")) : null,
        input.containsKey("autoApproveHours") ? (Boolean) input.get("autoApproveHours") : null);
    return trainerService.updateTrainerSettings(trainerId, command);
  }

  /**
   * Admin directly logs trainer hours (status set to {@code APPROVED}, bypasses
   * approval flow).
   *
   * @param input the hour logging input
   * @return the created trainer log entry
   */
  @MutationMapping
  public TrainerLog logTrainerHours(@Argument final Map<String, Object> input) {
    final var command = new LogTrainerHoursCommand(Long.valueOf(input.get("trainerId").toString()),
        Long.valueOf(input.get("sessionOccurrenceId").toString()), new BigDecimal(input.get("hoursWorked").toString()),
        (String) input.get("notes"));
    return trainerLogService.logTrainerHours(command);
  }

  /**
   * Trainer submits hours (status {@code PENDING} or auto-approved per trainer
   * settings).
   *
   * @param input the hour submission input
   * @return the created trainer log entry
   */
  @MutationMapping
  public TrainerLog submitTrainerHours(@Argument final Map<String, Object> input) {
    final var command = new LogTrainerHoursCommand(Long.valueOf(input.get("trainerId").toString()),
        Long.valueOf(input.get("sessionOccurrenceId").toString()), new BigDecimal(input.get("hoursWorked").toString()),
        (String) input.get("notes"));
    return trainerLogService.submitTrainerHours(command);
  }

  /**
   * Approves a pending trainer log entry.
   *
   * @param id the trainer log ID
   * @return the approved trainer log
   */
  @MutationMapping
  public TrainerLog approveTrainerLog(@Argument final Long id) {
    return trainerLogService.approveLog(id);
  }

  /**
   * Rejects a pending trainer log entry with a mandatory reason.
   *
   * @param input the rejection input containing log ID and reason
   * @return the rejected trainer log
   */
  @MutationMapping
  public TrainerLog rejectTrainerLog(@Argument final Map<String, Object> input) {
    return trainerLogService.rejectLog(Long.valueOf(input.get("id").toString()), (String) input.get("reason"));
  }

  /**
   * Resubmits corrected hours after rejection (resets to {@code PENDING}).
   *
   * @param input the resubmission input containing log ID, corrected hours, and
   *              notes
   * @return the resubmitted trainer log
   */
  @MutationMapping
  public TrainerLog resubmitTrainerLog(@Argument final Map<String, Object> input) {
    return trainerLogService.resubmitLog(Long.valueOf(input.get("id").toString()),
        new BigDecimal(input.get("hoursWorked").toString()), (String) input.get("notes"));
  }
}
