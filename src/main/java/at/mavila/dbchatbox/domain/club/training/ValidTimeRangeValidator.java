package at.mavila.dbchatbox.domain.club.training;

import static java.util.Objects.isNull;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that {@link CreateSessionCommand#endTime()} is strictly after {@link CreateSessionCommand#startTime()}.
 *
 * @since 2026-04-10
 */
public class ValidTimeRangeValidator implements ConstraintValidator<ValidTimeRange, CreateSessionCommand> {

  @Override
  public boolean isValid(final CreateSessionCommand command, final ConstraintValidatorContext context) {
    if (isNull(command) || isNull(command.startTime()) || isNull(command.endTime())) {
      return true; // null fields handled by @NotNull
    }
    return command.endTime().isAfter(command.startTime());
  }
}
