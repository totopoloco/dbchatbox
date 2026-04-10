package at.mavila.dbchatbox.domain.club.training;

import java.time.LocalDate;
import java.util.List;

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
public record CreateOccurrencesCommand(Long sessionId, LocalDate startDate, LocalDate endDate,
    List<LocalDate> skipDates) {
}
