package at.mavila.dbchatbox.infrastructure.security;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code app.security.api-key.*} properties.
 *
 * <p>The application refuses to start when {@code hmacSecret} is empty — the
 * {@code API_KEY_HMAC_SECRET} environment variable must be set (rule 93).
 * Never commit the real value; use a secrets manager in production.</p>
 *
 * @since 2026-06-28
 */
@ConfigurationProperties(prefix = "app.security.api-key")
@Validated
@Getter
@Setter
public class ApiKeyProperties {

    /**
     * HMAC-SHA256 pepper used to hash API keys before storage.
     * Read from {@code API_KEY_HMAC_SECRET}. Refused on startup if empty.
     */
    @NotEmpty(message =
        "app.security.api-key.hmac-secret must not be empty — set the API_KEY_HMAC_SECRET environment variable")
    private String hmacSecret;

    /**
     * HTTP request header that carries the raw API key. Defaults to {@code x-api-key}.
     */
    @NotEmpty
    private String header = "x-api-key";
}
