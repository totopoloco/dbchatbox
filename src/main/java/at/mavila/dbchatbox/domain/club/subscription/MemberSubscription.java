package at.mavila.dbchatbox.domain.club.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Links a member to a membership type for a specific period.
 *
 * <p>
 * One subscription = one period = one expected payment. When the period ends
 * and the member wants to continue, the
 * administrator creates a new subscription (renewal).
 * </p>
 *
 * <p>
 * Note: {@code memberId} is nullable to support GDPR purge (subscriptions with
 * payments survive member deletion with
 * {@code memberId} set to null).
 * </p>
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "member_subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSubscription {

  @Id
  @TsidGenerated
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id")
  private Member member;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "membership_type_id", nullable = false)
  private MembershipType membershipType;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "agreed_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal agreedPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 50)
  @Builder.Default
  private SubscriptionPaymentStatus paymentStatus = SubscriptionPaymentStatus.NOT_PAID;

  @Version
  @Column(nullable = false)
  private Short version;
}
