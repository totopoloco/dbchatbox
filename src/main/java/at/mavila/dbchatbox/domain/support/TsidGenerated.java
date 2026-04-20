package at.mavila.dbchatbox.domain.support;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks an entity's {@code @Id} field to use TSID generation.
 *
 * <p>
 * Apply this annotation alongside {@code @Id} on a {@code Long} field to have Hibernate automatically generate a TSID
 * value before insertion.
 * </p>
 *
 * <pre>
 * {
 *   &#64;code
 *   &#64;Id
 *   @TsidGenerated
 *   private Long id;
 * }
 * </pre>
 *
 * @since 2026-04-09
 */
@IdGeneratorType(TsidIdentifierGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface TsidGenerated {
}
