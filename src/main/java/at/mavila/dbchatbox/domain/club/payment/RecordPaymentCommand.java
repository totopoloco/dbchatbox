package at.mavila.dbchatbox.domain.club.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
public record RecordPaymentCommand(@NotNull(message = "Subscription ID is required")
Long memberSubscriptionId,

    @NotNull(message = "Payment amount is required") @Positive(message = "Payment amount must be positive")
    BigDecimal amount,

    String currency,

    @NotNull(message = "Payment date is required")
    LocalDate paymentDate,

    String notes) {
}
