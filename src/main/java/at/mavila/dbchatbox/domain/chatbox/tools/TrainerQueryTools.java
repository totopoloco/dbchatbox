package at.mavila.dbchatbox.domain.chatbox.tools;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLog;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLogService;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLogService.TrainerHoursSummary;
import at.mavila.dbchatbox.domain.club.trainer.TrainerLogService.TrainerPaymentSummary;
import at.mavila.dbchatbox.domain.club.trainer.TrainerService;
import lombok.RequiredArgsConstructor;

/**
 * AI tools that read trainer data — listing trainers, hour summaries, pending
 * logs, and payment summaries.
 *
 * @since 2026-04-20
 */
@Component
@RequiredArgsConstructor
public class TrainerQueryTools {

  private final TrainerService trainerService;
  private final TrainerLogService trainerLogService;

  /**
   * Lists all trainers.
   */
  @Tool(description = """
      List every trainer registered with the club.
      Returns id, name, email, and phone number.
      Use this for "who are our trainers?", "show me all trainers".
      """)
  public List<TrainerSummary> listTrainers() {
    return trainerService.findAll().stream()
        .map(this::toTrainerSummary)
        .toList();
  }

  /**
   * Approved-hours summary for a trainer in a date range.
   */
  @Tool(description = """
      Approved-hours summary for a trainer over a date range. Returns total approved hours,
      the number of approved sessions in the range, and the total amount owed (approved hours × hourly rate).
      Use this for "how many hours has Bob worked in March?".
      """)
  public TrainerHoursInfo trainerHours(
      @ToolParam(description = "The trainer's TSID.") final Long trainerId,
      @ToolParam(description = "Inclusive start date (YYYY-MM-DD).") final LocalDate from,
      @ToolParam(description = "Inclusive end date (YYYY-MM-DD).") final LocalDate to) {

    final TrainerHoursSummary summary = trainerLogService.getHoursSummary(trainerId, from, to);
    final Trainer trainer = summary.trainer();
    return new TrainerHoursInfo(
        trainer.getId(),
        "%s %s".formatted(trainer.getFirstName(), trainer.getLastName()),
        summary.from(),
        summary.to(),
        summary.totalHours(),
        summary.sessionCount(),
        summary.totalOwed());
  }

  /**
   * List trainer-log entries awaiting approval (optionally filtered by
   * trainer).
   */
  @Tool(description = """
      List trainer-log entries that are still pending admin approval.
      Pass a trainerId to see only one trainer's pending entries; leave null to see every pending entry.
      Use this for "what trainer logs need my approval?".
      """)
  public List<TrainerLogSummary> pendingTrainerLogs(
      @ToolParam(required = false, description = "Optional trainer TSID filter. Null = all trainers.")
      final Long trainerId) {
    return trainerLogService.findPendingLogs(trainerId).stream()
        .map(this::toTrainerLogSummary)
        .toList();
  }

  /**
   * Full payment summary for a trainer over a date range — both approved and
   * pending hours.
   */
  @Tool(description = """
      Full payment summary for a trainer over a date range. Returns approved hours and sessions,
      pending hours and sessions, the trainer's hourly rate, payment mode (PER_SESSION or MONTHLY),
      and the total currently owed (based on approved hours only).
      Use this for "how much will Bob be paid for March?", "show me Bob's payment summary".
      """)
  public TrainerPaymentInfo trainerPaymentSummary(
      @ToolParam(description = "The trainer's TSID.") final Long trainerId,
      @ToolParam(description = "Inclusive start date (YYYY-MM-DD).") final LocalDate from,
      @ToolParam(description = "Inclusive end date (YYYY-MM-DD).") final LocalDate to) {

    final TrainerPaymentSummary summary = trainerLogService.getPaymentSummary(trainerId, from, to);
    final Trainer trainer = summary.trainer();
    return new TrainerPaymentInfo(
        trainer.getId(),
        "%s %s".formatted(trainer.getFirstName(), trainer.getLastName()),
        summary.from(),
        summary.to(),
        summary.approvedHours(),
        summary.approvedSessions(),
        summary.hourlyRate(),
        summary.paymentMode(),
        summary.totalOwed(),
        summary.pendingHours(),
        summary.pendingSessions());
  }

  // ---------------------------------------------------------------
  // mapping
  // ---------------------------------------------------------------

  private TrainerSummary toTrainerSummary(final Trainer trainer) {
    return new TrainerSummary(
        trainer.getId(),
        trainer.getFirstName(),
        trainer.getLastName(),
        trainer.getEmail(),
        trainer.getPhoneNumber());
  }

  private TrainerLogSummary toTrainerLogSummary(final TrainerLog log) {
    final Trainer trainer = log.getTrainer();
    return new TrainerLogSummary(
        log.getId(),
        nonNull(trainer) ? trainer.getId() : null,
        nonNull(trainer) ? "%s %s".formatted(trainer.getFirstName(), trainer.getLastName()) : null,
        nonNull(log.getSessionOccurrence()) ? log.getSessionOccurrence().getId() : null,
        nonNull(log.getSessionOccurrence()) ? log.getSessionOccurrence().getDate() : null,
        log.getHoursWorked(),
        log.getStatus().name(),
        log.getSubmittedAt(),
        log.getNotes(),
        log.getRejectionReason());
  }

  // ---------------------------------------------------------------
  // DTOs
  // ---------------------------------------------------------------

  /**
   * Flat view of a {@link Trainer}.
   */
  public record TrainerSummary(
      Long id,
      String firstName,
      String lastName,
      String email,
      String phoneNumber) {
  }

  /**
   * Approved-hours summary.
   */
  public record TrainerHoursInfo(
      Long trainerId,
      String trainerName,
      LocalDate from,
      LocalDate to,
      BigDecimal totalHours,
      int sessionCount,
      BigDecimal totalOwed) {
  }

  /**
   * Pending trainer-log entry.
   */
  public record TrainerLogSummary(
      Long id,
      Long trainerId,
      String trainerName,
      Long sessionOccurrenceId,
      LocalDate sessionDate,
      BigDecimal hoursWorked,
      String status,
      LocalDateTime submittedAt,
      String notes,
      String rejectionReason) {
  }

  /**
   * Full payment summary (approved + pending).
   */
  public record TrainerPaymentInfo(
      Long trainerId,
      String trainerName,
      LocalDate from,
      LocalDate to,
      BigDecimal approvedHours,
      int approvedSessions,
      BigDecimal hourlyRate,
      String paymentMode,
      BigDecimal totalOwed,
      BigDecimal pendingHours,
      int pendingSessions) {
  }
}
