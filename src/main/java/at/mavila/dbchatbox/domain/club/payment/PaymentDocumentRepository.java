package at.mavila.dbchatbox.domain.club.payment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PaymentDocument} entities.
 *
 * @since 2026-04-13
 */
public interface PaymentDocumentRepository extends JpaRepository<PaymentDocument, Long> {

  /**
   * Finds all payment documents for a specific subscription.
   *
   * @param memberSubscriptionId the subscription ID
   * @return list of payment documents
   */
  List<PaymentDocument> findByMemberSubscriptionId(Long memberSubscriptionId);
}
