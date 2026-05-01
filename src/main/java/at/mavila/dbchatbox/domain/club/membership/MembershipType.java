package at.mavila.dbchatbox.domain.club.membership;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import at.mavila.dbchatbox.domain.club.training.Session;
import at.mavila.dbchatbox.domain.support.Auditable;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines a type of membership the club offers (e.g., "Free Games", "Training").
 *
 * <p>
 * Includes pricing, duration, linked sessions, and lifecycle status. New membership types start in
 * {@link MembershipTypeStatus#DRAFT} and must be explicitly activated before subscriptions can be created.
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
@Table(name = "membership_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipType extends Auditable {

  @Id
  @TsidGenerated
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private Integer duration;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private Unit unit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private MembershipTypeStatus status;

  @Column(name = "prorated_mode", nullable = false)
  @Builder.Default
  private Boolean proratedMode = false;

  @Column(name = "grace_period_days", nullable = false)
  @Builder.Default
  private Integer gracePeriodDays = 30;

  @ManyToMany
  @JoinTable(name = "membership_type_session", joinColumns = @JoinColumn(name = "membership_type_id"), inverseJoinColumns = @JoinColumn(name = "session_id"))
  @Builder.Default
  private Set<Session> sessions = new HashSet<>();

  @Version
  @Column(nullable = false)
  private Short version;
}
