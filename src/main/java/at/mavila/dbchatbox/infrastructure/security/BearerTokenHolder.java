package at.mavila.dbchatbox.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped accessor for the current Keycloak JWT.
 *
 * <p>
 * Reads the raw bearer token from {@link SecurityContextHolder} on demand so any Spring component
 * can forward the caller's token to the Keycloak Admin REST API without threading it through method
 * signatures (decision D-1). A new instance is created per HTTP request, so the
 * {@code SecurityContextHolder} read in {@link #getToken()} always reflects the current request.
 * </p>
 *
 * @since 2026-06-29
 */
@Component
@RequestScope
public class BearerTokenHolder {

    /**
     * Returns the raw JWT value for the current request.
     *
     * @return the raw bearer token
     * @throws IllegalStateException if the current authentication is not a {@link JwtAuthenticationToken}
     *         (e.g. the API-key path) — callers reachable on the API-key path must guard with
     *         {@link #isJwtPresent()} first
     */
    public String getToken() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException(
            "No JWT available in current request — BearerTokenHolder requires JWT authentication");
    }

    /**
     * Returns whether the current request carries a Keycloak JWT (rather than an API key).
     *
     * @return {@code true} when the current authentication is a {@link JwtAuthenticationToken}
     */
    public boolean isJwtPresent() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
    }
}
