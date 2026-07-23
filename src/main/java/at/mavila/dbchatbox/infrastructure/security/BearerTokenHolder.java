package at.mavila.dbchatbox.infrastructure.security;

import static java.util.Objects.isNull;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import at.mavila.dbchatbox.domain.club.tenant.Tenant;
import at.mavila.dbchatbox.domain.club.tenant.TenantService;
import lombok.RequiredArgsConstructor;

/**
 * Request-scoped accessor for the bearer token forwarded to the Keycloak Admin REST API.
 *
 * <p>On the JWT path the caller's own token is returned directly. On the API-key path
 * there is no user JWT, so a service-account token is obtained via the {@code club-m2m}
 * client-credentials grant and cached for the lifetime of the request.</p>
 *
 * @since 2026-06-29
 */
@Component
@RequestScope
@RequiredArgsConstructor
public class BearerTokenHolder {

    private final KeycloakAuthClient keycloakAuthClient;
    private final TenantService tenantService;
    private final KeycloakProperties keycloakProperties;

    /** Cached M2M token — obtained at most once per request. */
    private String cachedM2mToken;

    /**
     * Returns the bearer token for the current request.
     *
     * <p>If the request carries a Keycloak JWT the raw token value is returned.
     * Otherwise a service-account token is obtained from Keycloak using the
     * {@code club-m2m} client credentials and cached for the lifetime of this
     * request-scoped bean.</p>
     *
     * @return the raw bearer token value
     * @throws IllegalStateException if no tenant context is available on the API-key path
     */
    public String getToken() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        return m2mToken();
    }

    /**
     * Returns whether the current request carries a Keycloak JWT (rather than an API key).
     *
     * @return {@code true} when the current authentication is a {@link JwtAuthenticationToken}
     */
    public boolean isJwtPresent() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
    }

    private String m2mToken() {
        if (isNull(cachedM2mToken)) {
            cachedM2mToken = fetchM2mToken();
        }
        return cachedM2mToken;
    }

    private String fetchM2mToken() {
        final Long tenantId = TenantContext.getTenantId();
        if (isNull(tenantId)) {
            throw new IllegalStateException("No tenant context — cannot obtain M2M token");
        }
        final Tenant tenant = tenantService.requireById(tenantId);
        return keycloakAuthClient.clientCredentials(
            tenant.getKeycloakRealm(),
            keycloakProperties.getM2mClientId(),
            tenant.getM2mClientSecret()
        ).accessToken();
    }
}
