package at.mavila.dbchatbox.domain.club.training;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
   * Finds the next upcoming SCHEDULED occurrence for sessions available to a member.
   *
   * @param memberId
   *                   the member ID
   * @param today
   *                   the current date
   * @return the next upcoming occurrence, if any
   */
  @Query("SELECT so FROM SessionOccurrence so " + "WHERE so.session IN (" + "  SELECT DISTINCT s FROM Session s "
      + "  JOIN MembershipType mt ON s MEMBER OF mt.sessions "
      + "  JOIN MemberSubscription ms ON ms.membershipType = mt "
      + "  WHERE ms.member.id = :memberId AND ms.endDate >= :today"
      + ") AND so.status = 'SCHEDULED' AND so.date >= :today " + "ORDER BY so.date ASC LIMIT 1")
  Optional<SessionOccurrence> findNextForMember(@Param("memberId")
  Long memberId, @Param("today")
  LocalDate today);

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
