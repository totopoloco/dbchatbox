package at.mavila.dbchatbox.infrastructure.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import at.mavila.dbchatbox.domain.club.tenant.TenantRepository;

/**
 * Multi-issuer authentication manager resolver for Keycloak-backed multi-tenancy.
 *
 * <p>The app trusts one Keycloak realm per tenant. For each issuer seen in a token,
 * this component looks up the issuer in the {@link TenantRepository} and builds (once, cached)
 * a {@link JwtAuthenticationProvider} that validates tokens against that realm's JWKS.</p>
 *
 * <p>Tokens from issuers not in the {@code tenant} table are rejected (rule 81).
 * JWKS metadata is fetched lazily on first use, so the app starts without Keycloak
 * being available.</p>
 *
 * @since 2026-06-28
 */
@Component
@RequiredArgsConstructor
public class TenantAuthenticationManagerResolver
        implements AuthenticationManagerResolver<HttpServletRequest> {

    private final TenantRepository tenantRepository;

    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();

    private final JwtIssuerAuthenticationManagerResolver delegate =
        new JwtIssuerAuthenticationManagerResolver(this::forIssuer);

    @Override
    public AuthenticationManager resolve(final HttpServletRequest request) {
        return delegate.resolve(request);
    }

    /**
     * Builds (once, then caches) an {@link AuthenticationManager} for a given issuer.
     * Rejects issuers not registered in the {@code tenant} table (rule 81).
     *
     * @param issuer the JWT {@code iss} claim value
     * @return an AuthenticationManager that validates tokens for this issuer
     * @throws InvalidBearerTokenException if the issuer is unknown or Keycloak is unreachable
     */
    private AuthenticationManager forIssuer(final String issuer) {
        if (tenantRepository.findByIssuerUri(issuer).isEmpty()) {
            throw new InvalidBearerTokenException("Untrusted issuer: " + issuer);
        }
        return managers.computeIfAbsent(issuer, iss -> {
            try {
                final JwtDecoder decoder = JwtDecoders.fromIssuerLocation(iss);
                final JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
                provider.setJwtAuthenticationConverter(
                    KeycloakRealmRoleConverter.jwtAuthenticationConverter());
                return provider::authenticate;
            } catch (final RestClientException ex) {
                managers.remove(iss);
                throw new InvalidBearerTokenException(
                    "Keycloak unreachable for issuer %s: %s".formatted(iss, ex.getMessage()), ex);
            }
        });
    }
}
