package at.mavila.dbchatbox.domain.club.subscription;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link MemberSubscription} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface MemberSubscriptionRepository extends JpaRepository<MemberSubscription, Long> {

  /**
   * Finds all subscriptions for a member.
   *
   * @param memberId
   *                   the member ID
   * @return all subscriptions for the member
   */
  List<MemberSubscription> findByMemberId(Long memberId);

  /**
   * Finds active subscriptions for a member (endDate >= today).
   *
   * @param memberId
   *                   the member ID
   * @param today
   *                   the current date
   * @return active subscriptions
   */
  List<MemberSubscription> findByMemberIdAndEndDateGreaterThanEqual(Long memberId, LocalDate today);

  /**
   * Finds all active subscriptions across all members.
   *
   * @param today
   *                the current date
   * @return all active subscriptions
   */
  List<MemberSubscription> findByEndDateGreaterThanEqual(LocalDate today);

  /**
   * Checks if any subscriptions reference a given membership type.
   *
   * @param membershipTypeId
   *                           the membership type ID
   * @return {@code true} if subscriptions exist
   */
  boolean existsByMembershipTypeId(Long membershipTypeId);

  /**
   * Finds subscriptions for a member that have no payments, to support GDPR purge.
   *
   * @param memberId
   *                   the member ID
   * @return subscriptions without any payments
   */
  @Query("SELECT ms FROM MemberSubscription ms WHERE ms.member.id = :memberId "
      + "AND NOT EXISTS (SELECT p FROM Payment p WHERE p.memberSubscription.id = ms.id)")
  List<MemberSubscription> findByMemberIdWithoutPayments(@Param("memberId")
  Long memberId);

  /**
   * Finds subscriptions for a member that have payments, to support GDPR purge.
   *
   * @param memberId
   *                   the member ID
   * @return subscriptions that have at least one payment
   */
  @Query("SELECT ms FROM MemberSubscription ms WHERE ms.member.id = :memberId "
      + "AND EXISTS (SELECT p FROM Payment p WHERE p.memberSubscription.id = ms.id)")
  List<MemberSubscription> findByMemberIdWithPayments(@Param("memberId")
  Long memberId);
}
