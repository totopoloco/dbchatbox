package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;

/**
 * Command record for logging or submitting trainer hours.
 *
 * @param trainerId
 *                              the trainer ID
 * @param sessionOccurrenceId
 *                              the session occurrence ID
 * @param hoursWorked
 *                              the hours worked (positive, max 24)
 * @param notes
 *                              optional notes
 * @since 2026-04-10
 */
public record LogTrainerHoursCommand(Long trainerId, Long sessionOccurrenceId, BigDecimal hoursWorked, String notes) {
}
