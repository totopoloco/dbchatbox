package at.mavila.dbchatbox.infrastructure.web;

import static java.util.Objects.isNull;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Registers CORS mappings for the GraphQL endpoint ({@code /graphql}) and the
 * GraphiQL playground ({@code /graphiql}) using values from
 * {@link CorsProperties}.
 *
 * <p>
 * Spring for GraphQL has its own CORS layer that runs before the MVC one.
 * Both layers are configured: this class handles the MVC layer, while the
 * {@code spring.graphql.cors.*} properties (mirroring {@code app.cors.*}) cover
 * the GraphQL-specific layer. Having both in sync ensures preflight requests
 * are
 * handled correctly regardless of which layer intercepts them first.
 * </p>
 *
 * <p>
 * <strong>Security note:</strong> allowed origins are read exclusively from
 * externally configurable properties — nothing is hardcoded here. Override
 * {@code app.cors.allowed-origins} per deployment profile to keep each
 * environment's policy explicit.
 * </p>
 *
 * @see CorsProperties
 * @since 2026-05-03
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class CorsWebConfiguration implements WebMvcConfigurer {

  private static final String[] ALL_HEADERS = { "*" };

  private final CorsProperties corsProperties;

  /**
   * Registers CORS rules for {@code /graphql} (all configured methods) and
   * {@code /graphiql} (read-only: GET and OPTIONS only).
   *
   * @param registry the MVC CORS registry
   */
  @Override
  public void addCorsMappings(final CorsRegistry registry) {
    final String[] origins = corsProperties.getAllowedOrigins().toArray(String[]::new);
    final String[] methods = corsProperties.getAllowedMethods().toArray(String[]::new);
    final String[] headers = resolveAllowedHeaders();

    registry.addMapping("/graphql")
        .allowedOrigins(origins)
        .allowedMethods(methods)
        .allowedHeaders(headers)
        .allowCredentials(corsProperties.isAllowCredentials())
        .maxAge(corsProperties.getMaxAgeSeconds());

    registry.addMapping("/graphiql")
        .allowedOrigins(origins)
        .allowedMethods("GET", "OPTIONS")
        .allowedHeaders(headers)
        .allowCredentials(corsProperties.isAllowCredentials())
        .maxAge(corsProperties.getMaxAgeSeconds());
  }

  private String[] resolveAllowedHeaders() {
    if (isNull(corsProperties.getAllowedHeaders()) || corsProperties.getAllowedHeaders().isEmpty()) {
      return ALL_HEADERS;
    }
    return corsProperties.getAllowedHeaders().toArray(String[]::new);
  }

}
