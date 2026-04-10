package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDate;

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
public record CreateMemberCommand(String firstName, String lastName, String email, String phoneNumber,
    LocalDate memberSince, LocalDate memberUntil) {
}
