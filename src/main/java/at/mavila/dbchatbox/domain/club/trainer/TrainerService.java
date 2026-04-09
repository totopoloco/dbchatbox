package at.mavila.dbchatbox.domain.club.trainer;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.DuplicateEmailException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
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

  /**
   * Registers a new trainer.
   *
   * @param firstName
   *                           the first name
   * @param lastName
   *                           the last name
   * @param email
   *                           the email (unique)
   * @param phoneNumber
   *                           the phone number (optional)
   * @param hourlyRate
   *                           the hourly rate
   * @param paymentMode
   *                           the payment mode
   * @param autoApproveHours
   *                           whether hours are auto-approved
   * @return the created trainer
   * @throws DuplicateEmailException
   *                                   if the email is already in use
   */
  public Trainer createTrainer(final String firstName, final String lastName, final String email,
      final String phoneNumber, final BigDecimal hourlyRate, final TrainerPaymentMode paymentMode,
      final Boolean autoApproveHours) {
    if (trainerRepository.existsByEmail(email)) {
      throw new DuplicateEmailException(email);
    }

    final Trainer trainer = Trainer.builder().firstName(firstName).lastName(lastName).email(email)
        .phoneNumber(phoneNumber).hourlyRate(hourlyRate).paymentMode(paymentMode)
        .autoApproveHours(isNull(autoApproveHours) ? false : autoApproveHours).build();

    return trainerRepository.save(trainer);
  }

  /**
   * Updates an existing trainer's details.
   *
   * @param id
   *                           the trainer ID
   * @param firstName
   *                           new first name (null to keep current)
   * @param lastName
   *                           new last name (null to keep current)
   * @param email
   *                           new email (null to keep current)
   * @param phoneNumber
   *                           new phone number (null to keep current)
   * @param hourlyRate
   *                           new hourly rate (null to keep current)
   * @param paymentMode
   *                           new payment mode (null to keep current)
   * @param autoApproveHours
   *                           new auto-approve setting (null to keep current)
   * @return the updated trainer
   * @throws ResourceNotFoundException
   *                                     if the trainer does not exist
   * @throws DuplicateEmailException
   *                                     if the new email is already in use
   */
  public Trainer updateTrainer(final Long id, final String firstName, final String lastName, final String email,
      final String phoneNumber, final BigDecimal hourlyRate, final TrainerPaymentMode paymentMode,
      final Boolean autoApproveHours) {
    final Trainer trainer = trainerRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Trainer", id));

    if (nonNull(firstName)) {
      trainer.setFirstName(firstName);
    }
    if (nonNull(lastName)) {
      trainer.setLastName(lastName);
    }
    if (nonNull(email) && !email.equals(trainer.getEmail())) {
      if (trainerRepository.existsByEmailAndIdNot(email, id)) {
        throw new DuplicateEmailException(email);
      }
      trainer.setEmail(email);
    }
    if (nonNull(phoneNumber)) {
      trainer.setPhoneNumber(phoneNumber);
    }
    if (nonNull(hourlyRate)) {
      trainer.setHourlyRate(hourlyRate);
    }
    if (nonNull(paymentMode)) {
      trainer.setPaymentMode(paymentMode);
    }
    if (nonNull(autoApproveHours)) {
      trainer.setAutoApproveHours(autoApproveHours);
    }

    return trainerRepository.save(trainer);
  }

  /**
   * Lists all trainers.
   *
   * @return all trainers
   */
  @Transactional(readOnly = true)
  public List<Trainer> findAll() {
    return trainerRepository.findAll();
  }

  /**
   * Finds a trainer by ID.
   *
   * @param id
   *             the trainer ID
   * @return the trainer
   * @throws ResourceNotFoundException
   *                                     if not found
   */
  @Transactional(readOnly = true)
  public Trainer findById(final Long id) {
    return trainerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trainer", id));
  }
}
