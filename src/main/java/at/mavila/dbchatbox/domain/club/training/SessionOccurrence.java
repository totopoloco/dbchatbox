package at.mavila.dbchatbox.domain.club.training;

import java.time.LocalDate;

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
 * A concrete, date-specific instance of a {@link Session}.
 *
 * <p>
 * While {@code Session} defines the recurring weekly template, {@code SessionOccurrence} materializes each individual
 * date on which the session actually takes place.
 * </p>
 *
 * <p>
 * Inherits {@code createdAt} and {@code updatedAt} audit timestamps from
 * {@link at.mavila.dbchatbox.domain.support.Auditable}.
 * </p>
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "session_occurrence", uniqueConstraints = { @UniqueConstraint(columnNames = { "session_id", "date" }) })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionOccurrence extends Auditable {

  @Id
  @TsidGenerated
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private Session session;

  @Column(nullable = false)
  private LocalDate date;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private SessionOccurrenceStatus status;

  @Column(length = 500)
  private String notes;

  @Version
  @Column(nullable = false)
  private Short version;
}
