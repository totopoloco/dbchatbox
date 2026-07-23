package at.mavila.dbchatbox.domain.club.identity;

import at.mavila.dbchatbox.domain.support.Auditable;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thin link between a Keycloak subject and the domain {@link at.mavila.dbchatbox.domain.club.member.Member}
 * or {@link at.mavila.dbchatbox.domain.club.trainer.Trainer} identity.
 *
 * <p>No passwords are stored — credentials live entirely in Keycloak. This entity is
 * JIT-provisioned on first login via {@code me} and is used to enforce self-isolation
 * rules (rule 84): a member/trainer can only access their own personal data.</p>
 *
 * <p>Extends {@link Auditable} to inherit {@code tenant_id} — the tenant is auto-set
 * from {@link at.mavila.dbchatbox.infrastructure.security.TenantContext} on insert.</p>
 *
 * @since 2026-06-28
 */
@Entity
@Table(name = "app_user",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_app_user_tenant_subj",
            columnNames = { "tenant_id", "keycloak_subject" })
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser extends Auditable {

    @Id
    @TsidGenerated
    private Long id;

    /** The Keycloak {@code sub} claim for this user. Unique within a tenant. */
    @Column(name = "keycloak_subject", nullable = false, length = 64)
    private String keycloakSubject;

    /** Keycloak preferred_username. */
    @Column(nullable = false, length = 150)
    private String username;

    /** Keycloak email claim — may be null if not exposed. */
    @Column(length = 255)
    private String email;

    /**
     * The domain member linked to this user, or {@code null} if not linked.
     * Mutually exclusive with {@link #trainerId}.
     */
    @Column(name = "member_id")
    private Long memberId;

    /**
     * The domain trainer linked to this user, or {@code null} if not linked.
     * Mutually exclusive with {@link #memberId}.
     */
    @Column(name = "trainer_id")
    private Long trainerId;

    /** When {@code false}, this user is soft-disabled. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private Short version;
}
