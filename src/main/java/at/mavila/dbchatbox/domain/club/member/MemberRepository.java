package at.mavila.dbchatbox.domain.club.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Member} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

  /**
   * Checks whether a member with the given email exists.
   *
   * @param email
   *                the email to check
   * @return {@code true} if a member with that email exists
   */
  boolean existsByEmail(String email);

  /**
   * Finds a member by email address.
   *
   * @param email
   *                the email address
   * @return the member, if found
   */
  Optional<Member> findByEmail(String email);

  /**
   * Checks whether a member with the given email exists, excluding a specific member ID.
   *
   * @param email
   *                the email to check
   * @param id
   *                the member ID to exclude
   * @return {@code true} if another member with that email exists
   */
  @Query("SELECT COUNT(m) > 0 FROM Member m WHERE m.email = :email AND m.id <> :id")
  boolean existsByEmailAndIdNot(@Param("email")
  String email, @Param("id")
  Long id);
}
