package at.mavila.dbchatbox.domain.club.exception;

/**
 * Thrown when attempting to create a resource with a name that already exists.
 *
 * @since 2026-04-09
 */
public class DuplicateNameException extends RuntimeException {

  /**
   * Creates a new exception for the given resource type and name.
   *
   * @param resourceType
   *                       the type of resource (e.g., "MembershipType")
   * @param name
   *                       the duplicate name
   */
  public DuplicateNameException(final String resourceType, final String name) {
    super("%s name already exists: %s".formatted(resourceType, name));
  }
}
