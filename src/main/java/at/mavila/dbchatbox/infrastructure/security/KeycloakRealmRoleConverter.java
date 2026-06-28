package at.mavila.dbchatbox.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Converts a Keycloak JWT's {@code realm_access.roles} claim into Spring
 * {@code ROLE_*} authorities.
 *
 * <p>Keycloak puts realm roles in {@code realm_access.roles}, not in {@code scope}.
 * Without this converter the default Spring Security authority extraction reads
 * {@code scp} / {@code scope} only and sees no roles.</p>
 *
 * @since 2026-06-28
 */
public final class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Builds a {@link JwtAuthenticationConverter} that uses this converter for
     * authority extraction.
     *
     * @return a configured JwtAuthenticationConverter
     */
    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(final Jwt jwt) {
        final Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        final List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());
        return roles.stream()
            .map(role -> "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }
}
