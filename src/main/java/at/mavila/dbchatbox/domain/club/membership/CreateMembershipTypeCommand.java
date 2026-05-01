package at.mavila.dbchatbox.domain.club.membership;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Command record for creating a new membership type.
 *
 * @param name
 *                        the name (unique)
 * @param description
 *                        the description (optional)
 * @param price
 *                        the price per period
 * @param duration
 *                        the duration value
 * @param unit
 *                        the duration unit
 * @param proratedMode
 *                        whether automatic proration is enabled (optional)
 * @param gracePeriodDays
 *                        days after subscription start for payment (optional,
 *                        default 30)
 * @since 2026-04-10
 */
public record CreateMembershipTypeCommand(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must not exceed 100 characters") String name,

        @Size(max = 500, message = "Description must not exceed 500 characters") String description,

        @NotNull(message = "Price is required") @Positive(message = "Price must be positive") BigDecimal price,

        @NotNull(message = "Duration is required") @Positive(message = "Duration must be positive") Integer duration,

        @NotNull(message = "Unit is required") Unit unit,

        Boolean proratedMode,

        @Positive(message = "Grace period days must be positive") Integer gracePeriodDays) {
}
