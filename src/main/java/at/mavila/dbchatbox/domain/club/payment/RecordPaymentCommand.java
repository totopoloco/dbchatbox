package at.mavila.dbchatbox.domain.club.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Command record for recording a payment against a subscription.
 *
 * @param memberSubscriptionId
 *                               the subscription ID
 * @param amount
 *                               the payment amount (must be positive)
 * @param currency
 *                               the currency code (defaults to EUR)
 * @param paymentDate
 *                               the payment date
 * @param notes
 *                               optional notes
 * @since 2026-04-10
 */
public record RecordPaymentCommand(Long memberSubscriptionId, BigDecimal amount, String currency, LocalDate paymentDate,
    String notes) {
}
