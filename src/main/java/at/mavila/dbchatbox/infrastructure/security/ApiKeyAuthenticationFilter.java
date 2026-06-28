package at.mavila.dbchatbox.infrastructure.security;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import at.mavila.dbchatbox.domain.club.identity.ApiKey;
import at.mavila.dbchatbox.domain.club.identity.ApiKeyRepository;
import at.mavila.dbchatbox.domain.club.tenant.Tenant;
import at.mavila.dbchatbox.domain.club.tenant.TenantRepository;

/**
 * Authenticates M2M requests that carry a raw API key in the configured HTTP header
 * (default: {@code x-api-key}).
 *
 * <p>Authentication steps:</p>
 * <ol>
 *   <li>Read the raw key from the request header; skip if absent.</li>
 *   <li>Parse the tenant slug from the key prefix ({@code cmk.<slug>.<random>}).</li>
 *   <li>Look up the active {@link Tenant} by slug; reject if unknown or inactive.</li>
 *   <li>Compute HMAC-SHA256 of the raw key; look up {@link ApiKey} by hash.</li>
 *   <li>Verify the key is active and belongs to the resolved tenant.</li>
 *   <li>Set {@link TenantContext} and populate the {@link SecurityContextHolder}
 *       with an {@link ApiKeyAuthenticationToken}.</li>
 * </ol>
 *
 * <p>This filter runs <strong>before</strong>
 * {@code BearerTokenAuthenticationFilter} so that an already-authenticated
 * SecurityContext prevents the bearer filter from attempting JWT validation.</p>
 *
 * <p>TenantContext is cleared by {@link TenantResolutionFilter}'s {@code finally} block
 * after the request completes, so this filter must not clear it.</p>
 *
 * @since 2026-06-28
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final ApiKeyHmacService apiKeyHmacService;
    private final ApiKeyProperties apiKeyProperties;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain)
            throws ServletException, IOException {

        final String rawKey = request.getHeader(apiKeyProperties.getHeader());
        if (isNull(rawKey)) {
            chain.doFilter(request, response);
            return;
        }

        final Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (nonNull(existing) && existing.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        if (authenticateKey(rawKey, response)) {
            chain.doFilter(request, response);
        }
    }

    private boolean authenticateKey(final String rawKey, final HttpServletResponse response)
            throws IOException {
        final String slug = extractTenantSlug(rawKey);
        if (isNull(slug)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Malformed API key");
            return false;
        }

        final Tenant tenant = tenantRepository.findBySlugAndActiveIsTrue(slug).orElse(null);
        if (isNull(tenant)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown tenant in API key");
            return false;
        }

        final String hash = apiKeyHmacService.hmac(rawKey);
        final ApiKey apiKey = apiKeyRepository.findByKeyHash(hash)
            .filter(ApiKey::isActive)
            .filter(k -> tenant.getId().equals(k.getTenantId()))
            .orElse(null);

        if (isNull(apiKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            return false;
        }

        TenantContext.setTenantId(tenant.getId());
        final List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_M2M"),
            new SimpleGrantedAuthority("SCOPE_" + apiKey.getScope())
        );
        SecurityContextHolder.getContext().setAuthentication(
            ApiKeyAuthenticationToken.authenticated(apiKey.getId(), authorities));

        log.debug("API key authentication succeeded for tenant '{}', keyId={}", slug, apiKey.getId());
        return true;
    }

    private static String extractTenantSlug(final String rawKey) {
        if (isNull(rawKey) || !rawKey.startsWith("cmk.")) {
            return null;
        }
        final String[] parts = rawKey.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        return parts[1];
    }
}
