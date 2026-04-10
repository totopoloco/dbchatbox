package at.mavila.dbchatbox.domain.club.training;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Command record for creating a recurring session.
 *
 * @param name
 *                      the session name
 * @param sessionType
 *                      the session type (TRAINING or FREE_GAME)
 * @param dayOfWeek
 *                      the day of the week
 * @param startTime
 *                      the start time
 * @param endTime
 *                      the end time (must be after startTime)
 * @param location
 *                      the location
 * @param trainerId
 *                      the trainer ID (required for TRAINING, must be null for FREE_GAME)
 * @since 2026-04-10
 */
public record CreateSessionCommand(String name, SessionType sessionType, DayOfWeek dayOfWeek, LocalTime startTime,
    LocalTime endTime, String location, Long trainerId) {
}
