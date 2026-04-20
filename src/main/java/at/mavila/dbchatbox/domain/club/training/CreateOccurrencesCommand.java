package at.mavila.dbchatbox.domain.club.training;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Command record for bulk-creating session occurrences.
 *
 * @param sessionId
 *                    the session ID
 * @param startDate
 *                    the start date (inclusive)
 * @param endDate
 *                    the end date (inclusive)
 * @param skipDates
 *                    dates to exclude (optional)
 * @since 2026-04-10
 */
public record CreateOccurrencesCommand(@NotNull(message = "Session ID is required")
Long sessionId,

    @NotNull(message = "Start date is required")
    LocalDate startDate,

    @NotNull(message = "End date is required")
    LocalDate endDate,

    List<LocalDate> skipDates) {
}
