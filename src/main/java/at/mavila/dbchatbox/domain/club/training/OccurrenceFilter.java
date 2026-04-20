package at.mavila.dbchatbox.domain.club.training;

import java.time.LocalDate;

/**
 * Filter record for querying session occurrences.
 *
 * @param sessionId
 *                    the session ID (optional)
 * @param from
 *                    start date (optional)
 * @param to
 *                    end date (optional)
 * @param status
 *                    the status filter (optional)
 * @since 2026-04-10
 */
public record OccurrenceFilter(Long sessionId, LocalDate from, LocalDate to, SessionOccurrenceStatus status) {
}
