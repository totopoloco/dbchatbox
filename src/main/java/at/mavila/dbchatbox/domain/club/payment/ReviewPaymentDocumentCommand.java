package at.mavila.dbchatbox.domain.club.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Command record for reviewing (approving or rejecting) a payment document.
 *
 * @param memberSubscriptionId the subscription whose payment document is being
 *                             reviewed
 * @param approved             true to confirm payment (REVIEWED), false to
 *                             reject (NOT_PAID)
 * @param reason               required when rejecting; optional otherwise
 * @since 2026-04-13
 */
public record ReviewPaymentDocumentCommand(
    @NotNull(message = "Subscription ID is required") Long memberSubscriptionId,

    @NotNull(message = "Approval decision is required") Boolean approved,

    @Size(max = 500, message = "Reason must not exceed 500 characters") String reason) {
}
