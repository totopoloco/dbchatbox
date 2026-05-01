package at.mavila.dbchatbox.infrastructure.web.graphql;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import graphql.scalars.ExtendedScalars;

/**
 * Configuration for custom GraphQL scalar types.
 *
 * <p>
 * Registers extended scalar types (Date, LocalTime, BigDecimal, Long) from the graphql-java-extended-scalars library
 * and the custom {@link LocalDateTimeScalar} for the {@code DateTime} scalar.
 * </p>
 *
 * @since 2026-04-09
 */
@Configuration
public class ScalarConfiguration {

  private final LocalDateTimeScalar localDateTimeScalar;

  public ScalarConfiguration(final LocalDateTimeScalar localDateTimeScalar) {
    this.localDateTimeScalar = localDateTimeScalar;
  }

  /**
   * Configures the GraphQL runtime wiring with extended scalar types.
   *
   * @return a {@link RuntimeWiringConfigurer} that registers Date, DateTime, LocalTime, and BigDecimal scalars
   */
  @Bean
  public RuntimeWiringConfigurer runtimeWiringConfigurer() {
    return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.Date).scalar(localDateTimeScalar.toGraphQLScalarType())
        .scalar(ExtendedScalars.LocalTime).scalar(ExtendedScalars.GraphQLBigDecimal)
        .scalar(ExtendedScalars.GraphQLLong);
  }
}
