package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;

/**
 * Command record for registering a new trainer with initial settings.
 *
 * @param firstName
 *                           the first name
 * @param lastName
 *                           the last name
 * @param email
 *                           the email (unique)
 * @param phoneNumber
 *                           the phone number (optional)
 * @param hourlyRate
 *                           the hourly rate
 * @param paymentMode
 *                           the payment mode
 * @param autoApproveHours
 *                           whether hours are auto-approved (optional, defaults to false)
 * @since 2026-04-10
 */
public record CreateTrainerCommand(String firstName, String lastName, String email, String phoneNumber,
    BigDecimal hourlyRate, TrainerPaymentMode paymentMode, Boolean autoApproveHours) {
}
