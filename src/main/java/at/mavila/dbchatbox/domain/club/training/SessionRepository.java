package at.mavila.dbchatbox.domain.club.training;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

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
   * Finds all sessions of the given type within a specific tenant.
   *
   * @param sessionType
   *                      the session type filter
   * @param tenantId
   *                      the tenant ID
   * @return matching sessions for the tenant
   */
  List<Session> findBySessionTypeAndTenantId(SessionType sessionType, Long tenantId);

  /**
   * Finds all sessions belonging to a specific tenant.
   *
   * @param tenantId
   *                the tenant ID
   * @return all sessions for that tenant
   */
  List<Session> findAllByTenantId(Long tenantId);

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

  /**
   * Returns the IDs of trainers who already have at least one session that
   * overlaps the given day-of-week and time range.
   *
   * <p>
   * Used by {@link SessionService#findAvailableTrainers} as a one-shot
   * "busy set" query — replaces an earlier N+1 pattern that fetched every
   * trainer's day sessions individually. Overlap semantics match the
   * in-memory {@code timesOverlap} predicate (strict inequalities, so
   * sessions that touch on a boundary are NOT considered overlapping).
   * </p>
   *
   * @param dayOfWeek the day of the week
   * @param startTime the slot's start time (exclusive on the busy side)
   * @param endTime   the slot's end time (exclusive on the busy side)
   * @return IDs of trainers busy in the given slot
   */
  @Query("SELECT DISTINCT s.trainer.id FROM Session s "
      + "WHERE s.trainer IS NOT NULL "
      + "AND s.dayOfWeek = :dayOfWeek "
      + "AND s.startTime < :endTime AND s.endTime > :startTime")
  Set<Long> findBusyTrainerIdsForSlot(@Param("dayOfWeek")
  DayOfWeek dayOfWeek, @Param("startTime")
  LocalTime startTime, @Param("endTime")
  LocalTime endTime);
}
