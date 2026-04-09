package at.mavila.dbchatbox.domain.club.training;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Session} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

  /**
   * Finds all sessions of the given type.
   *
   * @param sessionType
   *                      the session type filter
   * @return matching sessions
   */
  List<Session> findBySessionType(SessionType sessionType);

  /**
   * Finds all sessions assigned to a trainer on a given day of the week.
   *
   * @param trainerId
   *                    the trainer ID
   * @param dayOfWeek
   *                    the day of the week
   * @return sessions for the trainer on that day
   */
  List<Session> findByTrainerIdAndDayOfWeek(Long trainerId, DayOfWeek dayOfWeek);

  /**
   * Finds all sessions at a given location on a given day of the week.
   *
   * @param location
   *                    the location
   * @param dayOfWeek
   *                    the day of the week
   * @return sessions at that location on that day
   */
  List<Session> findByLocationAndDayOfWeek(String location, DayOfWeek dayOfWeek);

  /**
   * Finds all sessions linked to membership types that a member has active subscriptions for.
   *
   * @param memberId
   *                   the member ID
   * @return sessions available to the member through their subscriptions
   */
  @Query("SELECT DISTINCT s FROM Session s " + "JOIN MembershipType mt ON s MEMBER OF mt.sessions "
      + "JOIN MemberSubscription ms ON ms.membershipType = mt "
      + "WHERE ms.member.id = :memberId AND ms.endDate >= CURRENT_DATE")
  List<Session> findSessionsByMemberId(@Param("memberId")
  Long memberId);
}
