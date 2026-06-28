package at.mavila.dbchatbox.domain.club.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ApiKey} entities.
 *
 * @since 2026-06-28
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /**
     * Looks up an API key by its stored HMAC hash — the primary lookup used during
     * request authentication. The hash is globally unique (see V7 migration constraint).
     *
     * @param keyHash the base64-encoded HMAC-SHA256 hash of the raw key
     * @return the matching key if it exists
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Returns all API keys visible to a given tenant — used by the
     * {@code apiKeys} GraphQL query.
     *
     * @param tenantId the owning tenant's primary key
     * @return API keys for that tenant, newest first (by createdAt in service layer)
     */
    List<ApiKey> findAllByTenantId(Long tenantId);
}
