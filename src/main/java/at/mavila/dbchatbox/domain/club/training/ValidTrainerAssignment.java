package at.mavila.dbchatbox.domain.club.training;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that the trainer assignment is consistent with the session type on a {@link CreateSessionCommand}.
 * <ul>
 * <li>{@code TRAINING} sessions require a non-null {@code trainerId}.</li>
 * <li>{@code FREE_GAME} sessions must not have a {@code trainerId}.</li>
 * </ul>
 *
 * @since 2026-04-10
 */
@Documented
@Constraint(validatedBy = ValidTrainerAssignmentValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTrainerAssignment {

  String message() default "Trainer assignment is inconsistent with session type";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
