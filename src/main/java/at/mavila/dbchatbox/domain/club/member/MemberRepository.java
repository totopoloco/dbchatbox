package at.mavila.dbchatbox.domain.club.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

  /**
   * Returns the IDs of members eligible for GDPR-purge anonymization: members
   * whose latest status entry is {@link Status#DELETED} and whose DELETED
   * timestamp is strictly older than {@code cutoff}, excluding members that
   * have already been anonymized (identified by {@code firstName} matching
   * the anonymization marker — which {@code MemberGdprService} sets when it
   * scrubs personal data).
   *
   * <p>
   * Used by {@code GdprPurgeJob} as a one-shot replacement for the previous
   * {@code findAll()} + per-member {@code getCurrentStatus} / latest-history
   * lookup pattern. One round trip regardless of member count, and the
   * already-anonymized filter prevents the job from rediscovering the same
   * rows on every nightly run.
   * </p>
   *
   * @param deletedStatus    the DELETED status sentinel (passed in to keep the
   *                         repository decoupled from {@code Status} ordering)
   * @param cutoff           the retention cutoff — only entries whose
   *                         {@code changedAt} is strictly before this are returned
   * @param anonymizedMarker the value {@code MemberGdprService} writes into
   *                         {@code firstName} when scrubbing PII (the
   *                         {@code DELETED_NAME} constant on that service)
   * @return member IDs ready to be anonymized
   */
  @Query("""
      SELECT m.id FROM Member m
      WHERE m.firstName <> :anonymizedMarker
      AND EXISTS (
        SELECT 1 FROM MemberStatusHistory h
        WHERE h.member = m
        AND h.status = :deletedStatus
        AND h.changedAt < :cutoff
        AND h.changedAt = (
          SELECT MAX(h2.changedAt) FROM MemberStatusHistory h2 WHERE h2.member = m
        )
      )
      """)
  List<Long> findGdprPurgeCandidateIds(@Param("deletedStatus")
  Status deletedStatus, @Param("cutoff")
  LocalDateTime cutoff, @Param("anonymizedMarker")
  String anonymizedMarker);
}
