package at.mavila.dbchatbox.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security configuration for the GraphQL API.
 *
 * <p>Authentication is token-based only (JWT from Keycloak, or API key via
 * {@link ApiKeyAuthenticationFilter}). There are no sessions and no CSRF risk.</p>
 *
 * <p>The HTTP layer stays permissive for all GraphQL paths because GraphQL is a single
 * endpoint — per-operation authorization must be method-level ({@code @PreAuthorize}),
 * not URL-level. The resource server still authenticates every token; an unauthenticated
 * call arrives with no authorities and is rejected by the per-method rules (except
 * {@code login}/{@code refreshToken}, which are intentionally public).</p>
 *
 * @since 2026-06-28
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({ApiKeyProperties.class, KeycloakProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantAuthenticationManagerResolver authManagerResolver;
    private final TenantResolutionFilter tenantResolutionFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    /**
     * Configures the security filter chain.
     *
     * @param http the HttpSecurity builder
     * @return the configured filter chain
     * @throws Exception on configuration error
     */
    @Bean
    SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> reg
                .requestMatchers("/graphql", "/graphiql", "/graphiql/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 ->
                oauth2.authenticationManagerResolver(authManagerResolver))
            .addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(tenantResolutionFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Prevents Spring Boot from auto-registering ApiKeyAuthenticationFilter as a Servlet
     * filter. It must run only inside the Spring Security filter chain (added via
     * {@code addFilterBefore} above), not as a standalone Servlet filter.
     */
    @Bean
    FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilterRegistration(
            final ApiKeyAuthenticationFilter filter) {
        final FilterRegistrationBean<ApiKeyAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /**
     * Prevents Spring Boot from auto-registering TenantResolutionFilter as a Servlet filter
     * for the same reason as {@link #apiKeyFilterRegistration}.
     */
    @Bean
    FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(
            final TenantResolutionFilter filter) {
        final FilterRegistrationBean<TenantResolutionFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
