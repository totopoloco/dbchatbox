package at.mavila.dbchatbox.domain.club.payment;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Payment} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  /**
   * Finds all payments for a specific subscription.
   *
   * @param memberSubscriptionId
   *                               the subscription ID
   * @return payments for the subscription
   */
  List<Payment> findByMemberSubscriptionId(Long memberSubscriptionId);

  /**
   * Finds all payments for a member across all their subscriptions.
   *
   * @param memberId
   *                   the member ID
   * @return all payments for the member
   */
  @Query("SELECT p FROM Payment p WHERE p.memberSubscription.member.id = :memberId")
  List<Payment> findByMemberId(@Param("memberId")
  Long memberId);

  /**
   * Calculates the total amount paid for a specific subscription.
   *
   * @param memberSubscriptionId
   *                               the subscription ID
   * @return total paid amount, or null if no payments exist
   */
  @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.memberSubscription.id = :subId")
  BigDecimal sumAmountByMemberSubscriptionId(@Param("subId")
  Long memberSubscriptionId);
}
