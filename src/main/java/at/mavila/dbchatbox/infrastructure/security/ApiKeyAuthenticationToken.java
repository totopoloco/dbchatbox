package at.mavila.dbchatbox.infrastructure.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication token set in the {@link org.springframework.security.core.context.SecurityContext}
 * when a request is authenticated via API key (Variant B — local-only auth).
 *
 * <p>The principal is the numeric {@link at.mavila.dbchatbox.domain.club.identity.ApiKey} ID.
 * Credentials are {@code null} — the key was already verified by
 * {@link ApiKeyAuthenticationFilter} before this token is created.</p>
 *
 * @since 2026-06-28
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final Long apiKeyId;

    private ApiKeyAuthenticationToken(final Long apiKeyId,
                                      final Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiKeyId = apiKeyId;
        setAuthenticated(true);
    }

    /**
     * Factory method — creates an authenticated token for the given API key ID and roles.
     *
     * @param apiKeyId    the primary key of the verified {@code ApiKey} entity
     * @param authorities granted authorities (e.g. {@code ROLE_M2M}, {@code SCOPE_READ})
     * @return an authenticated token
     */
    public static ApiKeyAuthenticationToken authenticated(final Long apiKeyId,
            final Collection<? extends GrantedAuthority> authorities) {
        return new ApiKeyAuthenticationToken(apiKeyId, authorities);
    }

    /** The API key ID. */
    @Override
    public Object getPrincipal() {
        return apiKeyId;
    }

    /** Always {@code null} — credentials are never retained after verification. */
    @Override
    public Object getCredentials() {
        return null;
    }
}
