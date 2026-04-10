package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
public record CreateTrainerCommand(
    @NotBlank(message = "First name is required") @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required") @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName,

    @NotBlank(message = "Email is required") @Email(message = "Email must be a valid email address")
    String email,

    String phoneNumber,

    @NotNull(message = "Hourly rate is required") @Positive(message = "Hourly rate must be positive")
    BigDecimal hourlyRate,

    @NotNull(message = "Payment mode is required")
    TrainerPaymentMode paymentMode,

    Boolean autoApproveHours) {
}
