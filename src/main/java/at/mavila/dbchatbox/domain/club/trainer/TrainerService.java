package at.mavila.dbchatbox.domain.club.trainer;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.DuplicateEmailException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for trainer management — registration, updates, queries.
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class TrainerService {

  private final TrainerRepository trainerRepository;
  private final TrainerSettingsRepository trainerSettingsRepository;
  private final CommandValidator commandValidator;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Registers a new trainer and creates the initial {@link TrainerSettings} in a
   * single transaction.
   *
   * @param command
   *                the creation command
   * @return the created trainer
   */
  public Trainer createTrainer(final CreateTrainerCommand command) {
    commandValidator.validate(command);

    if (trainerRepository.existsByEmailAndTenantId(command.email(), currentTenantOrThrow())) {
      throw new DuplicateEmailException(command.email());
    }

    final Trainer trainer = Trainer.builder().firstName(command.firstName()).lastName(command.lastName())
        .email(command.email()).phoneNumber(command.phoneNumber()).build();
    final Trainer saved = trainerRepository.save(trainer);

    final TrainerSettings settings = TrainerSettings.builder().trainer(saved).hourlyRate(command.hourlyRate())
        .paymentMode(command.paymentMode())
        .autoApproveHours(isNull(command.autoApproveHours()) ? false : command.autoApproveHours()).build();
    trainerSettingsRepository.save(settings);

    return saved;
  }

  /**
   * Updates a trainer's contact details only (name, email, phone).
   *
   * @param id
   *                the trainer ID
   * @param command
   *                the update command (null fields are not changed)
   * @return the updated trainer
   */
  public Trainer updateTrainer(final Long id, final UpdateTrainerCommand command) {
    final Trainer trainer = tenantScopedFinder.findById(trainerRepository, id)
        .orElseThrow(() -> new ResourceNotFoundException("Trainer", id));

    if (nonNull(command.firstName())) {
      trainer.setFirstName(command.firstName());
    }
    if (nonNull(command.lastName())) {
      trainer.setLastName(command.lastName());
    }
    if (nonNull(command.email()) && !command.email().equals(trainer.getEmail())) {
      final Long tenantId = currentTenantOrThrow();
      if (trainerRepository.existsByEmailAndIdNotAndTenantId(command.email(), id, tenantId)) {
        throw new DuplicateEmailException(command.email());
      }
      trainer.setEmail(command.email());
    }
    if (nonNull(command.phoneNumber())) {
      trainer.setPhoneNumber(command.phoneNumber());
    }

    return trainerRepository.save(trainer);
  }

  /**
   * Updates a trainer's compensation and workflow settings (admin-only).
   *
   * @param trainerId
   *                  the trainer ID
   * @param command
   *                  the settings update command (null fields are not changed)
   * @return the updated settings
   */
  public TrainerSettings updateTrainerSettings(final Long trainerId, final UpdateTrainerSettingsCommand command) {
    tenantScopedFinder.findById(trainerRepository, trainerId)
        .orElseThrow(() -> new ResourceNotFoundException("Trainer", trainerId));

    final TrainerSettings settings = trainerSettingsRepository.findByTrainerId(trainerId)
        .orElseThrow(() -> new ResourceNotFoundException("TrainerSettings", trainerId));

    if (nonNull(command.hourlyRate())) {
      settings.setHourlyRate(command.hourlyRate());
    }
    if (nonNull(command.paymentMode())) {
      settings.setPaymentMode(command.paymentMode());
    }
    if (nonNull(command.autoApproveHours())) {
      settings.setAutoApproveHours(command.autoApproveHours());
    }

    return trainerSettingsRepository.save(settings);
  }

  /**
   * Finds the settings for a given trainer.
   *
   * @param trainerId
   *                  the trainer ID
   * @return the trainer settings
   */
  @Transactional(readOnly = true)
  public TrainerSettings findSettingsByTrainerId(final Long trainerId) {
    tenantScopedFinder.findById(trainerRepository, trainerId)
        .orElseThrow(() -> new ResourceNotFoundException("Trainer", trainerId));

    return trainerSettingsRepository.findByTrainerId(trainerId)
        .orElseThrow(() -> new ResourceNotFoundException("TrainerSettings", trainerId));
  }

  /**
   * Returns all trainers within the current tenant.
   *
   * @return list of all trainers
   */
  @Transactional(readOnly = true)
  public List<Trainer> findAll() {
    return trainerRepository.findAllByTenantId(currentTenantOrThrow());
  }

  /**
   * Finds a trainer by ID.
   *
   * @param id the trainer ID
   * @return the trainer
   * @throws ResourceNotFoundException if the trainer does not exist
   */
  @Transactional(readOnly = true)
  public Trainer findById(final Long id) {
    return tenantScopedFinder.findById(trainerRepository, id)
        .orElseThrow(() -> new ResourceNotFoundException("Trainer", id));
  }

  private Long currentTenantOrThrow() {
    final Long t = TenantContext.getTenantId();
    if (isNull(t)) {
      throw new IllegalStateException("No tenant in context");
    }
    return t;
  }
}
