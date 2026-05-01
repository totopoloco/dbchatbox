package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDate;

/**
 * Command record for updating member details. Null fields are not changed.
 *
 * @param firstName
 *                      new first name (null to keep current)
 * @param lastName
 *                      new last name (null to keep current)
 * @param email
 *                      new email (null to keep current)
 * @param phoneNumber
 *                      new phone number (null to keep current)
 * @param memberSince
 *                      new member-since date (null to keep current)
 * @param memberUntil
 *                      new member-until date (null to keep current)
 * @since 2026-04-10
 */
public record UpdateMemberCommand(String firstName, String lastName, String email, String phoneNumber,
    LocalDate memberSince, LocalDate memberUntil) {
}
