package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import at.mavila.dbchatbox.domain.club.training.SessionOccurrence;
import at.mavila.dbchatbox.domain.support.Auditable;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records that a trainer conducted a specific session on a specific date.
 *
 * <p>
 * Linked to a {@link SessionOccurrence} (which provides the date) and a
 * {@link Trainer}. Follows an approval workflow:
 * PENDING → APPROVED or REJECTED. APPROVED is terminal.
 * </p>
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "trainer_log", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "trainer_id", "session_occurrence_id" }) })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainerLog extends Auditable {

  @Id
  @TsidGenerated
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trainer_id", nullable = false)
  private Trainer trainer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_occurrence_id", nullable = false)
  private SessionOccurrence sessionOccurrence;

  @Column(name = "hours_worked", nullable = false, precision = 5, scale = 2)
  private BigDecimal hoursWorked;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private TrainerLogStatus status;

  @Column(name = "submitted_at", nullable = false)
  private LocalDateTime submittedAt;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(length = 500)
  private String notes;

  @Version
  @Column(nullable = false)
  private Short version;
}
