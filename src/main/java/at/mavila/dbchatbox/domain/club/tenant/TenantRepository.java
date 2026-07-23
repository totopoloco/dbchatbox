package at.mavila.dbchatbox.domain.club.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Tenant} entities.
 *
 * @since 2026-06-28
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /**
     * Finds a tenant by its Keycloak issuer URI — used by the resource server to map
     * a JWT {@code iss} claim to a tenant.
     *
     * @param issuerUri the full issuer URI (e.g. {@code http://localhost:8088/realms/wat-simmering})
     * @return the matching tenant, if found
     */
    Optional<Tenant> findByIssuerUri(String issuerUri);

    /**
     * Finds a tenant by its URL slug (e.g. {@code wat-simmering}), used when the
     * caller supplies a {@code tenantSlug} in a login request.
     *
     * @param slug the tenant slug
     * @return the matching tenant, if found
     */
    Optional<Tenant> findBySlug(String slug);

    /**
     * Finds an active tenant by slug. Returns empty when the tenant is unknown or inactive.
     *
     * @param slug the tenant slug
     * @return the active tenant, if found
     */
    Optional<Tenant> findBySlugAndActiveIsTrue(String slug);
}
