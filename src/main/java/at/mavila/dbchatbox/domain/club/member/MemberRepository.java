package at.mavila.dbchatbox.domain.club.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for the lean {@link Member} stub.
 *
 * <p>
 * Email/identity lookups no longer live here — duplicate-email enforcement is delegated to Keycloak
 * (per-realm unique email). This repository retains only what the DB-backed slice needs: tenant
 * iteration and GDPR purge-candidate discovery.
 * </p>
 *
 * @since 2026-04-09
 */
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

  /**
   * Finds all members belonging to a specific tenant.
   *
   * @param tenantId
   *                the tenant ID
   * @return all members for that tenant
   */
  List<Member> findAllByTenantId(Long tenantId);

  /**
   * Returns the IDs of members eligible for GDPR-purge anonymization: members whose latest status
   * entry is {@link Status#DELETED} and whose DELETED timestamp is strictly older than
   * {@code cutoff}, excluding members that have already been anonymized (the {@code anonymized}
   * flag, set by {@code MemberGdprService} when it scrubs personal data).
   *
   * <p>
   * One round trip regardless of member count, and the already-anonymized filter prevents the job
   * from rediscovering the same rows on every nightly run.
   * </p>
   *
   * @param deletedStatus the DELETED status sentinel (passed in to keep the repository decoupled
   *                      from {@code Status} ordering)
   * @param cutoff        the retention cutoff — only entries whose {@code changedAt} is strictly
   *                      before this are returned
   * @return member IDs ready to be anonymized
   */
  @Query("""
      SELECT m.id FROM Member m
      WHERE m.anonymized = false
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
  LocalDateTime cutoff);
}
