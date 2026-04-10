package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;

/**
 * Command record for updating trainer compensation and workflow settings. Null fields are not changed.
 *
 * @param hourlyRate
 *                           new hourly rate (null to keep current)
 * @param paymentMode
 *                           new payment mode (null to keep current)
 * @param autoApproveHours
 *                           new auto-approve setting (null to keep current)
 * @since 2026-04-10
 */
public record UpdateTrainerSettingsCommand(BigDecimal hourlyRate, TrainerPaymentMode paymentMode,
    Boolean autoApproveHours) {
}
