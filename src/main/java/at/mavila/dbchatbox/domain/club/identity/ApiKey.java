package at.mavila.dbchatbox.domain.club.identity;

import java.time.OffsetDateTime;

import at.mavila.dbchatbox.domain.support.Auditable;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
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
 * Represents a machine-to-machine API key for a tenant.
 *
 * <p>Only the HMAC-SHA256 hash of the key is stored — never the raw value (rule 91).
 * A stolen database yields no usable keys because the HMAC pepper is not stored here.
 * The raw key is returned exactly once when generated and must be captured by the caller.</p>
 *
 * <p>Extends {@link Auditable} to inherit {@code tenant_id} — scoped to the tenant that
 * created it via {@link at.mavila.dbchatbox.infrastructure.security.TenantContext}.</p>
 *
 * @since 2026-06-28
 */
@Entity
@Table(name = "api_key")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey extends Auditable {

    @Id
    @TsidGenerated
    private Long id;

    /** Human-readable label for management purposes. */
    @Column(nullable = false, length = 120)
    private String label;

    /**
     * HMAC-SHA256 hash of the raw key (base64-encoded). Never the raw key.
     * The hash is unique across all tenants (see {@code uq_api_key_hash} constraint in V7).
     */
    @Column(name = "key_hash", nullable = false, length = 128, unique = true)
    private String keyHash;

    /**
     * The {@code club-m2m} client ID in the tenant's Keycloak realm (for Variant A
     * upgrade path). May be {@code null} for Variant B (local-only auth).
     */
    @Column(name = "keycloak_client_id", length = 120)
    private String keycloakClientId;

    /**
     * Authorization scope of this key — e.g. {@code READ}. API keys are read-only for the PoC
     * (rule 89). A {@code WRITE} scope may be introduced deliberately in the future.
     */
    @Column(nullable = false, length = 40)
    @Builder.Default
    private String scope = "READ";

    /** When {@code false}, authentication with this key is denied (revoked). */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Updated best-effort on each successful authentication (see {@code ApiKeyService}). */
    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Version
    @Column(nullable = false)
    private Short version;
}
