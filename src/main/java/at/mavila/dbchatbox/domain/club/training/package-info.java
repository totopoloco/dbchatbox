/**
 * Training domain — sessions, session occurrences, and calendar scheduling.
 *
 * <p>
 * Key components:
 * </p>
 * <ul>
 * <li>{@link at.mavila.dbchatbox.domain.club.training.Session} — recurring
 * weekly schedule slot (training or free game)</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.training.SessionOccurrence} —
 * concrete date-specific instance of a session</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.training.SessionService} — session
 * creation with overlap validation</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.training.SessionOccurrenceService}
 * — bulk creation, cancellation, and completion</li>
 * </ul>
 *
 * @since 2026-04-09
 */
package at.mavila.dbchatbox.domain.club.training;
