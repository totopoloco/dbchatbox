package at.mavila.dbchatbox.infrastructure.security;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code app.keycloak.*} properties used by {@link KeycloakAuthClient}.
 *
 * @since 2026-06-28
 */
@ConfigurationProperties(prefix = "app.keycloak")
@Validated
@Getter
@Setter
public class KeycloakProperties {

    /** Base URL of the Keycloak server, e.g. {@code http://localhost:8088}. */
    @NotEmpty
    private String baseUrl;

    /** Keycloak client ID for the SPA (direct-access grant). */
    @NotEmpty
    private String spaClientId;
}
