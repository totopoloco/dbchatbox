package at.mavila.dbchatbox.domain.club.trainer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Trainer} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface TrainerRepository extends JpaRepository<Trainer, Long> {

  /**
   * Checks whether a trainer with the given email exists.
   *
   * @param email
   *                the email to check
   * @return {@code true} if a trainer with that email exists
   */
  boolean existsByEmail(String email);

  /**
   * Finds a trainer by email address.
   *
   * @param email
   *                the email address
   * @return the trainer, if found
   */
  Optional<Trainer> findByEmail(String email);

  /**
   * Checks whether a trainer with the given email exists, excluding a specific ID.
   *
   * @param email
   *                the email to check
   * @param id
   *                the trainer ID to exclude
   * @return {@code true} if another trainer with that email exists
   */
  @Query("SELECT COUNT(t) > 0 FROM Trainer t WHERE t.email = :email AND t.id <> :id")
  boolean existsByEmailAndIdNot(@Param("email")
  String email, @Param("id")
  Long id);
}
