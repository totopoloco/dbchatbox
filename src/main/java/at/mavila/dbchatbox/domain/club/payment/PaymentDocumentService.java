package at.mavila.dbchatbox.domain.club.payment;

import static java.util.Objects.isNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionRepository;
import at.mavila.dbchatbox.domain.club.subscription.SubscriptionPaymentStatus;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for payment document upload and review workflow.
 *
 * <p>
 * Manages the lifecycle of payment proof documents: upload, storage, and admin
 * verification.
 * Uploading a document transitions the subscription's payment status from
 * {@code NOT_PAID} to {@code IN_REVIEW}.
 * Admin review transitions to {@code REVIEWED} (approved) or back to
 * {@code NOT_PAID} (rejected).
 * </p>
 *
 * @since 2026-04-13
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PaymentDocumentService {

  private static final String PDF_CONTENT_TYPE = "application/pdf";

  private final PaymentDocumentRepository paymentDocumentRepository;
  private final MemberSubscriptionRepository subscriptionRepository;
  private final CommandValidator commandValidator;
  private final TenantScopedFinder tenantScopedFinder;

  @Value("${app.payment.max-document-size-bytes:10485760}")
  private long maxDocumentSizeBytes;

  @Value("${app.payment.storage-path:payment-documents}")
  private String storagePath;

  /**
   * Uploads a payment proof document for a subscription.
   *
   * <p>
   * Validates that the subscription exists, its payment status is
   * {@code NOT_PAID},
   * and the file does not exceed the maximum size. Stores the file and
   * transitions
   * the subscription's payment status to {@code IN_REVIEW}.
   * </p>
   *
   * @param command the upload command
   * @return the created payment document
   * @throws ResourceNotFoundException if the subscription does not exist
   * @throws InvalidOperationException if payment status is not {@code NOT_PAID}
   *                                   or file exceeds max size
   */
  public PaymentDocument uploadDocument(final UploadPaymentDocumentCommand command) {
    commandValidator.validate(command);

    final MemberSubscription subscription = tenantScopedFinder.findById(subscriptionRepository,
            command.memberSubscriptionId())
        .orElseThrow(() -> new ResourceNotFoundException("MemberSubscription", command.memberSubscriptionId()));

    if (subscription.getPaymentStatus() != SubscriptionPaymentStatus.NOT_PAID) {
      throw new InvalidOperationException(
          "Can only upload payment documents when payment status is NOT_PAID, current status: %s"
              .formatted(subscription.getPaymentStatus().name()));
    }

    final byte[] fileBytes = Base64.getDecoder().decode(command.fileContent());

    if (fileBytes.length > maxDocumentSizeBytes) {
      throw new InvalidOperationException(
          "File size %d bytes exceeds maximum allowed size of %d bytes".formatted(fileBytes.length,
              maxDocumentSizeBytes));
    }

    final String filePath = storeFile(subscription.getId(), command.fileName(), fileBytes);

    final PaymentDocument document = PaymentDocument.builder()
        .memberSubscription(subscription)
        .fileName(command.fileName())
        .contentType(PDF_CONTENT_TYPE)
        .storagePath(filePath)
        .fileSize((long) fileBytes.length)
        .uploadedAt(LocalDateTime.now())
        .notes(command.notes())
        .build();

    final PaymentDocument saved = paymentDocumentRepository.save(document);

    subscription.setPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW);
    subscriptionRepository.save(subscription);

    return saved;
  }

  /**
   * Reviews a payment document — approves or rejects.
   *
   * <p>
   * If approved, transitions the subscription's payment status to
   * {@code REVIEWED}.
   * If rejected, transitions back to {@code NOT_PAID} (a reason is required).
   * </p>
   *
   * @param command the review command
   * @return the updated subscription
   * @throws ResourceNotFoundException if the subscription does not exist
   * @throws InvalidOperationException if payment status is not {@code IN_REVIEW}
   *                                   or rejection reason is missing
   */
  public MemberSubscription reviewDocument(final ReviewPaymentDocumentCommand command) {
    commandValidator.validate(command);

    final MemberSubscription subscription = tenantScopedFinder.findById(subscriptionRepository,
            command.memberSubscriptionId())
        .orElseThrow(() -> new ResourceNotFoundException("MemberSubscription", command.memberSubscriptionId()));

    if (subscription.getPaymentStatus() != SubscriptionPaymentStatus.IN_REVIEW) {
      throw new InvalidOperationException(
          "Can only review documents when payment status is IN_REVIEW, current status: %s"
              .formatted(subscription.getPaymentStatus().name()));
    }

    if (Boolean.TRUE.equals(command.approved())) {
      subscription.setPaymentStatus(SubscriptionPaymentStatus.REVIEWED);
    } else {
      if (isNull(command.reason()) || command.reason().isBlank()) {
        throw new InvalidOperationException("A reason is required when rejecting a payment document");
      }
      subscription.setPaymentStatus(SubscriptionPaymentStatus.NOT_PAID);
    }

    return subscriptionRepository.save(subscription);
  }

  /**
   * Lists all payment documents for a subscription.
   *
   * @param memberSubscriptionId the subscription ID
   * @return list of payment documents
   */
  @Transactional(readOnly = true)
  public List<PaymentDocument> findBySubscription(final Long memberSubscriptionId) {
    return paymentDocumentRepository.findByMemberSubscriptionId(memberSubscriptionId);
  }

  private String storeFile(final Long subscriptionId, final String fileName, final byte[] content) {
    try {
      final Path directory = Path.of(storagePath, subscriptionId.toString());
      Files.createDirectories(directory);
      final Path filePath = directory.resolve(fileName);
      Files.write(filePath, content);
      return filePath.toString();
    } catch (final IOException e) {
      throw new InvalidOperationException("Failed to store payment document: %s".formatted(e.getMessage()));
    }
  }
}
