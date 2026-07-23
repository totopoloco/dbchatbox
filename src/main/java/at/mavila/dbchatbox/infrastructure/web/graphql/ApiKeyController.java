package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.identity.ApiKey;
import at.mavila.dbchatbox.domain.club.identity.ApiKeyService;
import at.mavila.dbchatbox.domain.club.identity.AppUser;
import at.mavila.dbchatbox.domain.club.identity.AppUserService;
import at.mavila.dbchatbox.domain.club.identity.GeneratedApiKeyResult;

/**
 * GraphQL controller for M2M API key management and AppUser linking.
 *
 * <p>All operations require authentication. API key generation further requires
 * the caller to be an admin (enforced by convention — granular roles will be
 * added in a future iteration).</p>
 *
 * @since 2026-06-28
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AppUserService appUserService;

    /**
     * Returns all API keys for the current tenant.
     *
     * @return list of API keys (active and revoked)
     */
    @QueryMapping
    public List<ApiKey> apiKeys() {
        return apiKeyService.listApiKeys();
    }

    /**
     * Generates a new M2M API key. The raw key is returned once; it cannot be recovered later.
     *
     * @param input label for the new key
     * @return the generated key result containing both the raw key and the persisted entity
     */
    @MutationMapping
    public GeneratedApiKeyResult generateApiKey(@Argument final GenerateApiKeyInput input) {
        return apiKeyService.generateApiKey(input.label());
    }

    /**
     * Revokes an API key, preventing future authentication with it.
     *
     * @param id the API key's primary key
     * @return the updated (inactive) key entity
     */
    @MutationMapping
    public ApiKey revokeApiKey(@Argument final Long id) {
        return apiKeyService.revokeApiKey(id);
    }

    /**
     * Links a Keycloak identity to a domain Member or Trainer.
     *
     * @param input Keycloak subject + optional memberId/trainerId
     * @return the updated AppUser
     */
    @MutationMapping
    public AppUser linkAppUser(@Argument final LinkAppUserInput input) {
        return appUserService.link(input.keycloakSubject(), input.memberId(), input.trainerId());
    }

    private record GenerateApiKeyInput(String label) {}

    private record LinkAppUserInput(String keycloakSubject, Long memberId, Long trainerId) {}
}
