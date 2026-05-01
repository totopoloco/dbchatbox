package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when a referenced entity cannot be found.
 *
 * @since 2026-04-09
 */
public class ResourceNotFoundException extends RuntimeException {

  /**
   * Creates a new exception for the given resource type and ID.
   *
   * @param resourceType
   *                       the type of resource (e.g., "MembershipType", "Session")
   * @param id
   *                       the missing resource's ID
   */
  public ResourceNotFoundException(final String resourceType, final Long id) {
    super("%s not found: %d".formatted(resourceType, id));
  }
}
