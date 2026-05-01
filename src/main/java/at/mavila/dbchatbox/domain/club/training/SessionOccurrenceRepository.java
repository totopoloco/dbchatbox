package at.mavila.dbchatbox.domain.club.training;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link SessionOccurrence} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface SessionOccurrenceRepository extends JpaRepository<SessionOccurrence, Long> {

  /**
   * Checks if an occurrence exists for a given session and date.
   *
   * @param sessionId
   *                    the session ID
   * @param date
   *                    the date
   * @return {@code true} if an occurrence exists
   */
  boolean existsBySessionIdAndDate(Long sessionId, LocalDate date);

  /**
   * Finds occurrences for a session within a date range.
   *
   * @param sessionId
   *                    the session ID
   * @param from
   *                    start date (inclusive)
   * @param to
   *                    end date (inclusive)
   * @return matching occurrences
   */
  List<SessionOccurrence> findBySessionIdAndDateBetween(Long sessionId, LocalDate from, LocalDate to);

  /**
   * Finds occurrences for a session within a date range filtered by status.
   *
   * @param sessionId
   *                    the session ID
   * @param from
   *                    start date (inclusive)
   * @param to
   *                    end date (inclusive)
   * @param status
   *                    the status filter
   * @return matching occurrences
   */
  List<SessionOccurrence> findBySessionIdAndDateBetweenAndStatus(Long sessionId, LocalDate from, LocalDate to,
      SessionOccurrenceStatus status);

  /**
   * Checks if any occurrences exist for a given session.
   *
   * @param sessionId
   *                    the session ID
   * @return {@code true} if occurrences exist
   */
  boolean existsBySessionId(Long sessionId);

  /**
   * Finds upcoming SCHEDULED occurrences for sessions available to a member,
   * ordered by date ascending. Use the {@link Pageable} argument to cap the
   * result count — JPQL has no {@code LIMIT} clause, so the limit is applied
   * via {@code setMaxResults} by Spring Data instead.
   *
   * @param memberId the member ID
   * @param today    the current date (also the lower bound)
   * @param pageable used to limit the result set; e.g. {@code PageRequest.of(0, 1)}
   * @return matching occurrences in ascending date order
   */
  @Query("SELECT so FROM SessionOccurrence so " + "WHERE so.session IN (" + "  SELECT DISTINCT s FROM Session s "
      + "  JOIN MembershipType mt ON s MEMBER OF mt.sessions "
      + "  JOIN MemberSubscription ms ON ms.membershipType = mt "
      + "  WHERE ms.member.id = :memberId AND ms.endDate >= :today"
      + ") AND so.status = 'SCHEDULED' AND so.date >= :today " + "ORDER BY so.date ASC")
  List<SessionOccurrence> findUpcomingForMember(@Param("memberId")
  Long memberId, @Param("today")
  LocalDate today, Pageable pageable);

  /**
   * Convenience wrapper around {@link #findUpcomingForMember} that returns
   * the single next upcoming SCHEDULED occurrence. Implemented as a default
   * method so callers keep an {@link Optional} return without the repository
   * having to embed the row-limit in the query.
   *
   * @param memberId the member ID
   * @param today    the current date
   * @return the next upcoming occurrence, if any
   */
  default Optional<SessionOccurrence> findNextForMember(final Long memberId, final LocalDate today) {
    return findUpcomingForMember(memberId, today, PageRequest.of(0, 1)).stream().findFirst();
  }

  /**
   * Finds occurrences for sessions available to a member within a date range.
   *
   * @param memberId
   *                   the member ID
   * @param from
   *                   start date
   * @param to
   *                   end date
   * @return matching occurrences
   */
  @Query("SELECT so FROM SessionOccurrence so " + "WHERE so.session IN (" + "  SELECT DISTINCT s FROM Session s "
      + "  JOIN MembershipType mt ON s MEMBER OF mt.sessions "
      + "  JOIN MemberSubscription ms ON ms.membershipType = mt "
      + "  WHERE ms.member.id = :memberId AND ms.endDate >= CURRENT_DATE" + ") AND so.date BETWEEN :from AND :to "
      + "AND so.status IN ('SCHEDULED', 'COMPLETED') " + "ORDER BY so.date ASC")
  List<SessionOccurrence> findByMemberAndDateRange(@Param("memberId")
  Long memberId, @Param("from")
  LocalDate from, @Param("to")
  LocalDate to);
}
