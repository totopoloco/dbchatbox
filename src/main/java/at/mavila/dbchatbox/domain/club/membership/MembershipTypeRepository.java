package at.mavila.dbchatbox.domain.club.membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link MembershipType} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface MembershipTypeRepository extends JpaRepository<MembershipType, Long> {

  /**
   * Finds all membership types with the given status.
   *
   * @param status
   *                 the membership type status
   * @return matching membership types
   */
  List<MembershipType> findByStatus(MembershipTypeStatus status);

  /**
   * Checks whether a membership type with the given name exists.
   *
   * @param name
   *               the name to check
   * @return {@code true} if a type with that name exists
   */
  boolean existsByName(String name);

  /**
   * Finds a membership type by name.
   *
   * @param name
   *               the name
   * @return the membership type, if found
   */
  Optional<MembershipType> findByName(String name);
}
