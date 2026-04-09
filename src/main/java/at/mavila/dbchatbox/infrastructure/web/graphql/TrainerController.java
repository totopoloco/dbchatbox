package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLog;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLogService;
import at.mavila.dbchatbox.domain.club.trainer.TrainerPaymentMode;
import at.mavila.dbchatbox.domain.club.trainer.TrainerService;
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

  @QueryMapping
  public List<Trainer> trainers() {
    return trainerService.findAll();
  }

  @QueryMapping
  public Map<String, Object> trainerHours(@Argument
  final Long trainerId, @Argument
  final LocalDate from, @Argument
  final LocalDate to) {
    final var summary = trainerLogService.getHoursSummary(trainerId, from, to);
    return Map.of("trainer", summary.trainer(), "totalHours", summary.totalHours(), "sessionCount",
        summary.sessionCount(), "totalOwed", summary.totalOwed(), "from", summary.from(), "to", summary.to());
  }

  @QueryMapping
  public List<TrainerLog> pendingTrainerLogs(@Argument
  final Long trainerId) {
    return trainerLogService.findPendingLogs(trainerId);
  }

  @QueryMapping
  public Map<String, Object> trainerPaymentSummary(@Argument
  final Long trainerId, @Argument
  final LocalDate from, @Argument
  final LocalDate to) {
    final var summary = trainerLogService.getPaymentSummary(trainerId, from, to);
    return Map.of("trainer", summary.trainer(), "from", summary.from(), "to", summary.to(), "approvedHours",
        summary.approvedHours(), "approvedSessions", summary.approvedSessions(), "hourlyRate", summary.hourlyRate(),
        "totalOwed", summary.totalOwed(), "paymentMode", summary.paymentMode(), "pendingHours", summary.pendingHours(),
        "pendingSessions", summary.pendingSessions());
  }

  // ==================== MUTATIONS ====================

  @MutationMapping
  public Trainer createTrainer(@Argument
  final Map<String, Object> input) {
    return trainerService.createTrainer((String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"),
        new BigDecimal(input.get("hourlyRate").toString()),
        TrainerPaymentMode.valueOf((String) input.get("paymentMode")),
        input.containsKey("autoApproveHours") ? (Boolean) input.get("autoApproveHours") : null);
  }

  @MutationMapping
  public Trainer updateTrainer(@Argument
  final Long id, @Argument
  final Map<String, Object> input) {
    return trainerService.updateTrainer(id, (String) input.get("firstName"), (String) input.get("lastName"),
        (String) input.get("email"), (String) input.get("phoneNumber"),
        input.containsKey("hourlyRate") ? new BigDecimal(input.get("hourlyRate").toString()) : null,
        input.containsKey("paymentMode") ? TrainerPaymentMode.valueOf((String) input.get("paymentMode")) : null,
        input.containsKey("autoApproveHours") ? (Boolean) input.get("autoApproveHours") : null);
  }

  @MutationMapping
  public TrainerLog logTrainerHours(@Argument
  final Map<String, Object> input) {
    return trainerLogService.logTrainerHours(Long.valueOf(input.get("trainerId").toString()),
        Long.valueOf(input.get("sessionOccurrenceId").toString()), new BigDecimal(input.get("hoursWorked").toString()),
        (String) input.get("notes"));
  }

  @MutationMapping
  public TrainerLog submitTrainerHours(@Argument
  final Map<String, Object> input) {
    return trainerLogService.submitTrainerHours(Long.valueOf(input.get("trainerId").toString()),
        Long.valueOf(input.get("sessionOccurrenceId").toString()), new BigDecimal(input.get("hoursWorked").toString()),
        (String) input.get("notes"));
  }

  @MutationMapping
  public TrainerLog approveTrainerLog(@Argument
  final Long id) {
    return trainerLogService.approveLog(id);
  }

  @MutationMapping
  public TrainerLog rejectTrainerLog(@Argument
  final Map<String, Object> input) {
    return trainerLogService.rejectLog(Long.valueOf(input.get("id").toString()), (String) input.get("reason"));
  }

  @MutationMapping
  public TrainerLog resubmitTrainerLog(@Argument
  final Map<String, Object> input) {
    return trainerLogService.resubmitLog(Long.valueOf(input.get("id").toString()),
        new BigDecimal(input.get("hoursWorked").toString()), (String) input.get("notes"));
  }
}
