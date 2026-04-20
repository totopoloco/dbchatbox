package at.mavila.dbchatbox.domain.club.trainer;

/**
 * Command record for updating trainer contact details. Null fields are not changed.
 *
 * @param firstName
 *                      new first name (null to keep current)
 * @param lastName
 *                      new last name (null to keep current)
 * @param email
 *                      new email (null to keep current)
 * @param phoneNumber
 *                      new phone number (null to keep current)
 * @since 2026-04-10
 */
public record UpdateTrainerCommand(String firstName, String lastName, String email, String phoneNumber) {
}
