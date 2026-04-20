package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
public record LogTrainerHoursCommand(@NotNull(message = "Trainer ID is required")
Long trainerId,

    @NotNull(message = "Session occurrence ID is required")
    Long sessionOccurrenceId,

    @NotNull(message = "Hours worked is required") @Positive(message = "Hours worked must be positive") @DecimalMax(value = "24", message = "Hours worked cannot exceed 24")
    BigDecimal hoursWorked,

    String notes) {
}
