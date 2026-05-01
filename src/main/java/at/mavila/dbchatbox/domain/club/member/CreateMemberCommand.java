package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Command record for member registration.
 *
 * @param firstName
 *                      the first name (required)
 * @param lastName
 *                      the last name (required)
 * @param email
 *                      the email address (required, unique)
 * @param phoneNumber
 *                      the phone number (optional)
 * @param memberSince
 *                      the date the member joined (required)
 * @param memberUntil
 *                      the date membership expires (optional)
 * @since 2026-04-10
 */
public record CreateMemberCommand(
    @NotBlank(message = "First name is required") @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required") @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName,

    @NotBlank(message = "Email is required") @Email(message = "Email must be a valid email address")
    String email,

    String phoneNumber,

    @NotNull(message = "Member-since date is required")
    LocalDate memberSince,

    LocalDate memberUntil) {
}
