package at.mavila.dbchatbox.domain.club.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.support.Auditable;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records a payment made against a member subscription.
 *
 * <p>
 * A subscription can have multiple payments (e.g., partial payments).
 * Outstanding dues per subscription =
 * {@code agreedPrice} minus the sum of all payments.
 * </p>
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends Auditable {

  @Id
  @TsidGenerated
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_subscription_id", nullable = false)
  private MemberSubscription memberSubscription;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  @Builder.Default
  private String currency = "EUR";

  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  @Column(length = 500)
  private String notes;

  @Version
  @Column(nullable = false)
  private Short version;
}
