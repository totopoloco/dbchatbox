package at.mavila.dbchatbox.infrastructure.web;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * Strongly-typed configuration for Cross-Origin Resource Sharing (CORS),
 * populated from {@code app.cors.*} properties at startup.
 *
 * <p>
 * A class with JavaBean-style accessors is used rather than a record because
 * {@code @ConfigurationProperties} binding with Jakarta Bean Validation works
 * most reliably with getter/setter semantics in Spring Boot 4. Lombok's
 * {@code @Getter}/{@code @Setter} keeps the boilerplate minimal.
 * </p>
 *
 * <p>
 * Constraint validation fires at startup: the application refuses to start if
 * {@code allowedOrigins} or {@code allowedMethods} are empty, preventing a
 * silently misconfigured CORS policy.
 * </p>
 *
 * <p>
 * <strong>Security note:</strong> never combine {@code allowedOrigins = ["*"]}
 * with {@code allowCredentials = true} — browsers reject this combination and
 * Spring will throw an {@code IllegalArgumentException}.
 * </p>
 *
 * @see CorsWebConfiguration
 * @since 2026-05-03
 */
@ConfigurationProperties(prefix = "app.cors")
@Validated
@Getter
@Setter
public class CorsProperties {

  /**
   * Comma-separated list of origins that are permitted to call the GraphQL
   * endpoint. At least one value is required.
   * Example: {@code http://localhost:3000,https://app.example.com}
   */
  @NotEmpty(message = "At least one allowed CORS origin must be configured (app.cors.allowed-origins)")
  private List<String> allowedOrigins;

  /**
   * HTTP methods permitted in cross-origin requests. Defaults to
   * {@code GET, POST, OPTIONS}. At least one value is required.
   */
  @NotEmpty(message = "At least one allowed CORS method must be configured (app.cors.allowed-methods)")
  private List<String> allowedMethods;

  /**
   * HTTP request headers that cross-origin clients are allowed to send.
   * Use {@code *} to allow all headers, or enumerate explicit header names
   * (e.g. {@code Content-Type,Authorization}) for tighter production policies.
   */
  private List<String> allowedHeaders;

  /**
   * Whether the browser is allowed to include credentials (cookies,
   * HTTP authentication) in cross-origin requests. Must not be combined
   * with a wildcard origin.
   */
  private boolean allowCredentials;

  /**
   * How long (in seconds) browsers may cache the preflight response.
   * Must be non-negative. Defaults to 3600 (one hour).
   */
  @Min(value = 0, message = "CORS max-age must be non-negative (app.cors.max-age-seconds)")
  private long maxAgeSeconds;

}
