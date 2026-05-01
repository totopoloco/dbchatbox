package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.util.Locale;

/**
 * Custom GraphQL scalar type named {@code DateTime} that serializes and parses {@link LocalDateTime} values.
 *
 * <p>
 * {@code ExtendedScalars.DateTime} only accepts {@code OffsetDateTime}, but every {@code DateTime!} field in
 * the schema is backed by a Java {@code LocalDateTime}. This scalar handles both types — {@code LocalDateTime}
 * is serialized as an ISO-8601 string without an offset (e.g. {@code "2026-05-01T11:05:32.123456"});
 * {@code OffsetDateTime} is converted to local date-time first to retain backward compatibility.
 * </p>
 *
 * <p>
 * The scalar is named {@code "DateTime"} to match the {@code scalar DateTime} declaration in the schema.
 * No schema changes are required.
 * </p>
 *
 * @since 2026-05-01
 */
@Component
public class LocalDateTimeScalar {

  /**
   * Builds and returns the {@link GraphQLScalarType} named {@code "DateTime"}.
   *
   * @return the scalar type to register in the runtime wiring
   */
  public GraphQLScalarType toGraphQLScalarType() {
    return GraphQLScalarType.newScalar()
        .name("DateTime")
        .description("ISO-8601 local date-time scalar (e.g. 2026-05-01T11:05:32). Backed by java.time.LocalDateTime.")
        .coercing(new LocalDateTimeCoercing())
        .build();
  }

  private static final class LocalDateTimeCoercing implements Coercing<LocalDateTime, String> {

    @Override
    public String serialize(final Object dataFetcherResult, final GraphQLContext context, final Locale locale)
        throws CoercingSerializeException {
      if (dataFetcherResult instanceof LocalDateTime ldt) {
        return ldt.toString();
      }
      if (dataFetcherResult instanceof OffsetDateTime odt) {
        return odt.toLocalDateTime().toString();
      }
      throw new CoercingSerializeException(
          "Expected LocalDateTime or OffsetDateTime but got '%s'.".formatted(dataFetcherResult.getClass().getName()));
    }

    @Override
    public LocalDateTime parseValue(final Object input, final GraphQLContext context, final Locale locale)
        throws CoercingParseValueException {
      if (input instanceof String s) {
        try {
          return LocalDateTime.parse(s);
        } catch (final Exception e) {
          throw new CoercingParseValueException("Cannot parse DateTime value '%s': %s".formatted(s, e.getMessage()));
        }
      }
      throw new CoercingParseValueException("Expected a String for DateTime input but got '%s'."
          .formatted(input.getClass().getName()));
    }

    @Override
    public LocalDateTime parseLiteral(final Value<?> input, final CoercedVariables variables,
        final GraphQLContext context, final Locale locale) throws CoercingParseLiteralException {
      if (input instanceof StringValue sv) {
        try {
          return LocalDateTime.parse(sv.getValue());
        } catch (final Exception e) {
          throw new CoercingParseLiteralException(
              "Cannot parse DateTime literal '%s': %s".formatted(sv.getValue(), e.getMessage()));
        }
      }
      throw new CoercingParseLiteralException("Expected a StringValue AST node for DateTime literal.");
    }
  }
}
