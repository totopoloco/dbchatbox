package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDateTime;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks every status transition for a member, providing a full audit trail.
 *
 * <p>
 * The current status of a member is determined by the most recent entry (ordered by {@code changedAt} descending).
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
@Table(name = "member_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberStatusHistory extends Auditable {

  @Id
  @TsidGenerated
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private Status status;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  @Column(length = 500)
  private String reason;

  @Version
  @Column(nullable = false)
  private Short version;
}
