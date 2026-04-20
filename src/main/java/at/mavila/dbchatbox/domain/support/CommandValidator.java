package at.mavila.dbchatbox.domain.support;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

/**
 * Shared validation component that delegates to the Jakarta Bean {@link Validator} and throws
 * {@link InvalidOperationException} on constraint violations.
 *
 * <p>
 * Domain services inject this component instead of the raw {@link Validator} to keep validation invocation uniform and
 * error mapping centralized.
 * </p>
 *
 * @since 2026-04-10
 */
@Component
@RequiredArgsConstructor
public class CommandValidator {

  private final Validator validator;

  /**
   * Validates the given command object and throws {@link InvalidOperationException} if any constraint violations are
   * found.
   *
   * @param command
   *                  the command to validate
   * @param <T>
   *                  the command type
   * @throws InvalidOperationException
   *                                     if one or more constraint violations exist
   */
  public <T> void validate(final T command) {
    final Set<ConstraintViolation<T>> violations = validator.validate(command);
    if (violations.isEmpty()) {
      return;
    }
    final String message = violations.stream().map(ConstraintViolation::getMessage).sorted()
        .collect(Collectors.joining("; "));
    throw new InvalidOperationException(message);
  }
}
