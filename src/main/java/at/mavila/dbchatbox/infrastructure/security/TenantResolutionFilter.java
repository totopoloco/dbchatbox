package at.mavila.dbchatbox.infrastructure.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import at.mavila.dbchatbox.domain.club.tenant.Tenant;
import at.mavila.dbchatbox.domain.club.tenant.TenantRepository;

/**
 * Resolves the current tenant from a validated JWT and stamps {@link TenantContext}.
 *
 * <p>This filter runs <strong>after</strong> {@code BearerTokenAuthenticationFilter} has
 * authenticated the token. It reads the issuer from the {@link JwtAuthenticationToken},
 * looks up the active tenant, and sets {@link TenantContext}. If no active tenant can be
 * found for the issuer, the request is rejected with {@code 401} (fail closed, rule 80–82).</p>
 *
 * <p>The API-key filter sets {@code TenantContext} on its own code path; both filters
 * never populate it for the same request (bearer wins — rule 94).</p>
 *
 * @since 2026-06-28
 */
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain)
            throws ServletException, IOException {
        try {
            final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                final String issuer = jwtAuth.getToken().getIssuer().toString();
                final Tenant tenant = tenantRepository.findByIssuerUri(issuer)
                    .filter(Tenant::isActive)
                    .orElse(null);
                if (tenant == null) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unresolved tenant");
                    return;
                }
                TenantContext.setTenantId(tenant.getId());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
