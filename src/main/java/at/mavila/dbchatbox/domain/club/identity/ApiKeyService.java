package at.mavila.dbchatbox.domain.club.identity;

import static java.util.Objects.isNull;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.tenant.Tenant;
import at.mavila.dbchatbox.domain.club.tenant.TenantRepository;
import at.mavila.dbchatbox.infrastructure.security.ApiKeyHmacService;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for managing M2M {@link ApiKey} entities.
 *
 * <p>Raw key generation follows the format {@code cmk.<tenantSlug>.<32-char-base64url>}.
 * Only the HMAC-SHA256 hash of the raw key is persisted; the plaintext is returned once
 * inside {@link GeneratedApiKeyResult} and never stored (rule 91).</p>
 *
 * @since 2026-06-28
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApiKeyService {

    private static final int RANDOM_BYTES = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final ApiKeyHmacService apiKeyHmacService;
    private final TenantScopedFinder tenantScopedFinder;

    /**
     * Generates a new API key for the current tenant.
     *
     * <p>The raw key is returned exactly once via {@link GeneratedApiKeyResult#rawKey()}.
     * The caller must relay it to the client immediately — it cannot be recovered later.</p>
     *
     * @param label human-readable label for management purposes
     * @return the raw key bundled with the persisted entity
     * @throws IllegalStateException     if TenantContext is not set
     * @throws ResourceNotFoundException if the current tenant cannot be found
     */
    public GeneratedApiKeyResult generateApiKey(final String label) {
        final Long tenantId = TenantContext.getTenantId();
        if (isNull(tenantId)) {
            throw new IllegalStateException("Cannot generate API key without a tenant in context");
        }
        final Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        final String rawKey = buildRawKey(tenant.getSlug());
        final String hash = apiKeyHmacService.hmac(rawKey);

        final ApiKey saved = apiKeyRepository.save(ApiKey.builder()
            .label(label)
            .keyHash(hash)
            .scope("READ")
            .active(true)
            .build());

        return new GeneratedApiKeyResult(rawKey, saved);
    }

    /**
     * Revokes the API key identified by {@code id}, scoped to the current tenant.
     *
     * @param id the API key's primary key
     * @return the updated (inactive) entity
     * @throws ResourceNotFoundException if the key does not exist in the current tenant
     */
    public ApiKey revokeApiKey(final Long id) {
        final ApiKey key = tenantScopedFinder.findById(apiKeyRepository, id)
            .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
        key.setActive(false);
        return apiKeyRepository.save(key);
    }

    /**
     * Returns all API keys for the current tenant.
     *
     * @return list of API keys, may be empty
     */
    @Transactional(readOnly = true)
    public List<ApiKey> listApiKeys() {
        final Long tenantId = TenantContext.getTenantId();
        return apiKeyRepository.findAllByTenantId(tenantId);
    }

    /**
     * Touches the {@code lastUsedAt} timestamp of the given API key.
     * Called after a successful key-based authentication (best-effort).
     *
     * @param apiKeyId the key to update
     */
    public void recordUsage(final Long apiKeyId) {
        apiKeyRepository.findById(apiKeyId).ifPresent(key -> {
            key.setLastUsedAt(OffsetDateTime.now());
            apiKeyRepository.save(key);
        });
    }

    private static String buildRawKey(final String tenantSlug) {
        final byte[] bytes = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        final String random = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "cmk.%s.%s".formatted(tenantSlug, random);
    }
}
