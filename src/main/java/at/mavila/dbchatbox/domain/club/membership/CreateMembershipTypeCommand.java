package at.mavila.dbchatbox.domain.club.membership;

import java.math.BigDecimal;

/**
 * Command record for creating a new membership type.
 *
 * @param name
 *                       the name (unique)
 * @param description
 *                       the description (optional)
 * @param price
 *                       the price per period
 * @param duration
 *                       the duration value
 * @param unit
 *                       the duration unit
 * @param proratedMode
 *                       whether automatic proration is enabled (optional)
 * @since 2026-04-10
 */
public record CreateMembershipTypeCommand(String name, String description, BigDecimal price, Integer duration,
    Unit unit, Boolean proratedMode) {
}
