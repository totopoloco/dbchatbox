package at.mavila.dbchatbox.domain.club.training;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that the trainer assignment matches the session type:
 * <ul>
 * <li>{@code TRAINING} requires a non-null {@code trainerId}.</li>
 * <li>{@code FREE_GAME} requires a null {@code trainerId}.</li>
 * </ul>
 *
 * @since 2026-04-10
 */
public class ValidTrainerAssignmentValidator
    implements ConstraintValidator<ValidTrainerAssignment, CreateSessionCommand> {

  @Override
  public boolean isValid(final CreateSessionCommand command, final ConstraintValidatorContext context) {
    if (isNull(command) || isNull(command.sessionType())) {
      return true; // null fields handled by @NotNull
    }

    final boolean valid;
    if (command.sessionType() == SessionType.TRAINING) {
      valid = nonNull(command.trainerId());
      if (!valid) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("TRAINING sessions require a trainerId").addConstraintViolation();
      }
    } else if (command.sessionType() == SessionType.FREE_GAME) {
      valid = isNull(command.trainerId());
      if (!valid) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("FREE_GAME sessions must not have a trainerId")
            .addConstraintViolation();
      }
    } else {
      valid = true;
    }
    return valid;
  }
}
