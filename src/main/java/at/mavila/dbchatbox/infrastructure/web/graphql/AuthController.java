package at.mavila.dbchatbox.infrastructure.web.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.identity.AppUser;
import at.mavila.dbchatbox.domain.club.identity.AppUserService;
import at.mavila.dbchatbox.domain.club.tenant.Tenant;
import at.mavila.dbchatbox.domain.club.tenant.TenantService;
import at.mavila.dbchatbox.infrastructure.security.AuthPayload;
import at.mavila.dbchatbox.infrastructure.security.KeycloakAuthClient;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;

/**
 * GraphQL controller for authentication and identity queries.
 *
 * <p>{@code login} and {@code refreshToken} are intentionally public — they are the
 * entry points used to obtain tokens. All other operations require authentication.</p>
 *
 * @since 2026-06-28
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AppUserService appUserService;
    private final TenantService tenantService;
    private final KeycloakAuthClient keycloakAuthClient;

    /**
     * Returns the {@link AppUser} for the currently authenticated JWT principal,
     * JIT-provisioning it on first access.
     *
     * @return the current user's identity record
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public AppUser me() {
        return appUserService.currentUser();
    }

    /**
     * Returns the {@link Tenant} resolved from the current authentication token.
     *
     * @return the current tenant
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Tenant currentTenant() {
        return tenantService.requireById(TenantContext.getTenantId());
    }

    /**
     * Authenticates against a Keycloak realm via the resource-owner password credentials grant.
     * Public — no prior authentication required.
     *
     * @param input login credentials and tenant slug
     * @return access and refresh tokens
     */
    @MutationMapping
    public AuthPayload login(@Argument final LoginInput input) {
        final Tenant tenant = tenantService.requireBySlug(input.tenantSlug());
        return keycloakAuthClient.login(tenant.getKeycloakRealm(), input.username(), input.password());
    }

    /**
     * Exchanges a Keycloak refresh token for a new access token.
     * Public — no prior authentication required.
     *
     * @param input tenant slug and existing refresh token
     * @return new access and refresh tokens
     */
    @MutationMapping
    public AuthPayload refreshToken(@Argument final RefreshTokenInput input) {
        final Tenant tenant = tenantService.requireBySlug(input.tenantSlug());
        return keycloakAuthClient.refresh(tenant.getKeycloakRealm(), input.refreshToken());
    }

    /** Input record for the {@code login} mutation. */
    private record LoginInput(String tenantSlug, String username, String password) {}

    /** Input record for the {@code refreshToken} mutation. */
    private record RefreshTokenInput(String tenantSlug, String refreshToken) {}
}
