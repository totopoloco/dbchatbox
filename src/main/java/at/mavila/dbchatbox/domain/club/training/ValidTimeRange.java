package at.mavila.dbchatbox.domain.club.training;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that {@code endTime} is after {@code startTime} on a {@link CreateSessionCommand}.
 *
 * @since 2026-04-10
 */
@Documented
@Constraint(validatedBy = ValidTimeRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeRange {

  String message() default "endTime must be after startTime";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
