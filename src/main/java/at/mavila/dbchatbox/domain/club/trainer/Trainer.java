package at.mavila.dbchatbox.domain.club.trainer;

import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Represents a club trainer who leads training sessions and logs hours.
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "trainer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trainer {

  @Id
  @TsidGenerated
  private Long id;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "phone_number")
  private String phoneNumber;

  @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
  private BigDecimal hourlyRate;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_mode", nullable = false, length = 50)
  private TrainerPaymentMode paymentMode;

  @Column(name = "auto_approve_hours", nullable = false)
  @Builder.Default
  private Boolean autoApproveHours = false;
}
