package at.mavila.dbchatbox.domain.club.member;

import at.mavila.dbchatbox.domain.support.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lean reference to a club member. Since Keycloak became the single source of truth for member
 * identity, this entity no longer holds any personal data (name, email, phone, membership dates) —
 * those live in Keycloak as user attributes and are read via the Admin REST API.
 *
 * <p>
 * What remains here is purely the stable join target that downstream domain tables
 * ({@code member_status_history}, {@code member_subscription}, {@code app_user}) reference by FK:
 * the TSID primary key, the link to the Keycloak subject, the tenant scope, and the GDPR
 * anonymization flag. The TSID equals the {@code memberId} attribute stored against the matching
 * Keycloak user, bridging Keycloak identity to all DB foreign keys.
 * </p>
 *
 * <p>
 * Status is <strong>not</strong> stored directly on this entity; it is derived from the most recent
 * {@link MemberStatusHistory} entry. Inherits {@code createdAt}, {@code updatedAt}, and
 * {@code tenantId} from {@link at.mavila.dbchatbox.domain.support.Auditable}.
 * </p>
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member extends Auditable {

  /**
   * Primary key, assigned (not generated) from the Keycloak {@code memberId} attribute — the member
   * identity owned by Keycloak. Equals the {@code memberId} of the matching realm user.
   */
  @Id
  private Long id;

  /**
   * The Keycloak user id (the {@code sub} claim) of the matching realm user. Nullable until the
   * provisioning flow links it; unique per tenant.
   */
  @Column(name = "keycloak_subject", length = 64)
  private String keycloakSubject;

  /**
   * Set to {@code true} once the member's personal data has been GDPR-erased in Keycloak. Replaces
   * the former {@code firstName == "DELETED"} sentinel that {@code GdprPurgeJob} used to detect
   * already-scrubbed members.
   */
  @Column(nullable = false)
  @Builder.Default
  private boolean anonymized = false;

  @Version
  @Column(nullable = false)
  private Short version;
}
