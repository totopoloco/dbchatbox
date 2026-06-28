package at.mavila.dbchatbox.domain.club.tenant;

import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for tenant resolution — read-only helpers that fail closed when a
 * tenant is missing or inactive. Never returns a "default" tenant.
 *
 * @since 2026-06-28
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;

    /**
     * Returns the tenant for the given primary key, or throws if not found.
     *
     * @param id the tenant's primary key
     * @return the tenant
     * @throws ResourceNotFoundException if no tenant exists with that id
     */
    public Tenant requireById(final Long id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
    }

    /**
     * Returns the active tenant for the given URL slug, or throws if unknown or inactive.
     *
     * @param slug the URL-safe tenant identifier
     * @return the active tenant
     * @throws ResourceNotFoundException if no active tenant exists for that slug
     */
    public Tenant requireBySlug(final String slug) {
        return tenantRepository.findBySlugAndActiveIsTrue(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));
    }

    /**
     * Returns the active tenant for the given JWT issuer URI, or throws if unknown or inactive.
     *
     * @param issuerUri the Keycloak realm issuer URI from the token {@code iss} claim
     * @return the active tenant
     * @throws ResourceNotFoundException if no active tenant exists for that issuer
     */
    public Tenant requireByIssuer(final String issuerUri) {
        return tenantRepository.findByIssuerUri(issuerUri)
            .filter(Tenant::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant (by issuer)", issuerUri));
    }
}
