package at.mavila.dbchatbox.domain.club.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Command record for subscribing a member to a membership type.
 *
 * @param memberId
 *                           the member ID
 * @param membershipTypeId
 *                           the membership type ID
 * @param startDate
 *                           the start date
 * @param endDate
 *                           the end date (null to compute from duration)
 * @param agreedPrice
 *                           the agreed price (null to compute from type or prorate)
 * @since 2026-04-10
 */
public record SubscribeMemberCommand(Long memberId, Long membershipTypeId, LocalDate startDate, LocalDate endDate,
    BigDecimal agreedPrice) {
}
