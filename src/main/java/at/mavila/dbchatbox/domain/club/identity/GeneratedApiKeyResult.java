package at.mavila.dbchatbox.domain.club.identity;

/**
 * Carry type returned by {@link ApiKeyService#generateApiKey} that bundles the
 * raw API key with the persisted {@link ApiKey} entity.
 *
 * <p>The raw key is exposed exactly once here; the caller (controller) must relay
 * it to the client and never store it. The entity provides IDs, label, scope, and
 * audit timestamps for the GraphQL response.</p>
 *
 * @param rawKey the plaintext API key — returned to the caller ONCE, never persisted
 * @param apiKey the persisted entity (without the raw key)
 * @since 2026-06-28
 */
public record GeneratedApiKeyResult(String rawKey, ApiKey apiKey) {
}
