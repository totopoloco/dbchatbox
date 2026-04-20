package at.mavila.dbchatbox.domain.club.training;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
@ValidTimeRange
@ValidTrainerAssignment
public record CreateSessionCommand(
    @NotBlank(message = "Session name is required") @Size(max = 100, message = "Session name must not exceed 100 characters")
    String name,

    @NotNull(message = "Session type is required")
    SessionType sessionType,

    @NotNull(message = "Day of week is required")
    DayOfWeek dayOfWeek,

    @NotNull(message = "Start time is required")
    LocalTime startTime,

    @NotNull(message = "End time is required")
    LocalTime endTime,

    @NotBlank(message = "Location is required")
    String location,

    Long trainerId) {
}
