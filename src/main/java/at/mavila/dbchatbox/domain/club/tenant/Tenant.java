package at.mavila.dbchatbox.domain.club.tenant;

import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a tenant (a sports club) that owns a distinct, isolated dataset.
 *
 * <p>Each tenant maps one-to-one with a Keycloak realm. This entity is deliberately NOT
 * tenant-scoped itself — it is the root of the tenant dimension. It does not extend
 * {@link at.mavila.dbchatbox.domain.support.Auditable} to avoid the circularity of a
 * tenant having its own {@code tenant_id}.</p>
 *
 * @since 2026-06-28
 */
@Entity
@Table(name = "tenant",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_tenant_slug",   columnNames = "slug"),
        @UniqueConstraint(name = "uq_tenant_realm",  columnNames = "keycloak_realm"),
        @UniqueConstraint(name = "uq_tenant_issuer", columnNames = "issuer_uri")
    })
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    /**
     * TSID-generated primary key. The three seed rows (ids 1, 2, 3) are inserted by the
     * V7 Flyway migration using plain integers — {@link at.mavila.dbchatbox.domain.support.TsidIdentifierGenerator}
     * only fires on JPA-managed INSERTs and does not validate existing values on read.
     */
    @Id
    @TsidGenerated
    private Long id;

    /** URL-safe tenant identifier used in Keycloak realm names and API key prefixes. */
    @NotBlank
    @Size(max = 60)
    @Column(nullable = false)
    private String slug;

    /** Human-readable display name of the club. */
    @NotBlank
    @Size(max = 150)
    @Column(nullable = false)
    private String name;

    /** Keycloak realm name — must match what Keycloak stores. */
    @NotBlank
    @Size(max = 60)
    @Column(name = "keycloak_realm", nullable = false)
    private String keycloakRealm;

    /**
     * The {@code iss} claim value in tokens issued by this tenant's realm
     * (e.g. {@code http://localhost:8088/realms/wat-simmering}).
     * Used by {@code TenantAuthenticationManagerResolver} to look up the manager
     * for incoming JWTs.
     */
    @NotBlank
    @Size(max = 300)
    @Column(name = "issuer_uri", nullable = false)
    private String issuerUri;

    /** When {@code false}, all logins and data access for this tenant are denied. */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Client secret for the {@code club-m2m} Keycloak client in this tenant's realm.
     * Used by the API-key authentication path to obtain a service-account token when
     * no user JWT is present.
     */
    @Size(max = 200)
    @Column(name = "m2m_client_secret")
    private String m2mClientSecret;
}
