package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;

import at.mavila.dbchatbox.domain.support.Auditable;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores compensation and workflow configuration for a trainer.
 *
 * <p>
 * One-to-one with {@link Trainer}. Separates admin-managed settings (hourly rate, payment mode, auto-approve) from the
 * trainer's identity.
 * </p>
 *
 * <p>
 * Inherits {@code createdAt} and {@code updatedAt} audit timestamps from
 * {@link at.mavila.dbchatbox.domain.support.Auditable}.
 * </p>
 *
 * @since 2026-04-10
 */
@Entity
@Table(name = "trainer_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainerSettings extends Auditable {

  @Id
  @TsidGenerated
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trainer_id", nullable = false, unique = true)
  private Trainer trainer;

  @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
  private BigDecimal hourlyRate;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_mode", nullable = false, length = 50)
  private TrainerPaymentMode paymentMode;

  @Column(name = "auto_approve_hours", nullable = false)
  @Builder.Default
  private Boolean autoApproveHours = false;

  @Version
  @Column(nullable = false)
  private Short version;
}
