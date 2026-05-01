package at.mavila.dbchatbox.domain.club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionRepository;
import at.mavila.dbchatbox.domain.club.subscription.SubscriptionPaymentStatus;
import at.mavila.dbchatbox.domain.support.CommandValidator;

@ExtendWith(MockitoExtension.class)
class PaymentDocumentServiceTest {

  @Mock
  private PaymentDocumentRepository paymentDocumentRepository;

  @Mock
  private MemberSubscriptionRepository subscriptionRepository;

  @Mock
  private CommandValidator commandValidator;

  @InjectMocks
  private PaymentDocumentService service;

  @TempDir
  Path tempDir;

  private MemberSubscription subscription;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "maxDocumentSizeBytes", 10_485_760L);
    ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());

    subscription = MemberSubscription.builder()
        .id(1L)
        .startDate(LocalDate.of(2026, 1, 1))
        .endDate(LocalDate.of(2026, 12, 31))
        .agreedPrice(BigDecimal.valueOf(200))
        .paymentStatus(SubscriptionPaymentStatus.NOT_PAID)
        .build();
  }

  @Nested
  class UploadDocument {

    @Test
    void shouldUploadDocumentAndTransitionToInReview() {
      when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
      when(paymentDocumentRepository.save(any(PaymentDocument.class))).thenAnswer(inv -> inv.getArgument(0));
      when(subscriptionRepository.save(any(MemberSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

      final String content = Base64.getEncoder().encodeToString("PDF content".getBytes());
      final var command = new UploadPaymentDocumentCommand(1L, "payment.pdf", content, "Bank transfer");

      final PaymentDocument result = service.uploadDocument(command);

      assertThat(result.getFileName()).isEqualTo("payment.pdf");
      assertThat(result.getContentType()).isEqualTo("application/pdf");
      assertThat(subscription.getPaymentStatus()).isEqualTo(SubscriptionPaymentStatus.IN_REVIEW);
      verify(subscriptionRepository).save(subscription);
    }

    @Test
    void shouldRejectWhenSubscriptionNotFound() {
      when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());

      final String content = Base64.getEncoder().encodeToString("PDF".getBytes());
      final var command = new UploadPaymentDocumentCommand(999L, "payment.pdf", content, null);

      assertThatThrownBy(() -> service.uploadDocument(command))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldRejectWhenPaymentStatusNotNotPaid() {
      subscription.setPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW);
      when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));

      final String content = Base64.getEncoder().encodeToString("PDF".getBytes());
      final var command = new UploadPaymentDocumentCommand(1L, "payment.pdf", content, null);

      assertThatThrownBy(() -> service.uploadDocument(command))
          .isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("NOT_PAID");
    }

    @Test
    void shouldRejectWhenFileTooLarge() {
      ReflectionTestUtils.setField(service, "maxDocumentSizeBytes", 5L);
      when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));

      final String content = Base64.getEncoder().encodeToString("This content exceeds 5 bytes".getBytes());
      final var command = new UploadPaymentDocumentCommand(1L, "payment.pdf", content, null);

      assertThatThrownBy(() -> service.uploadDocument(command))
          .isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("exceeds maximum");
    }
  }

  @Nested
  class ReviewDocument {

    @Test
    void shouldApproveAndTransitionToReviewed() {
      subscription.setPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW);
      when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
      when(subscriptionRepository.save(any(MemberSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

      final var command = new ReviewPaymentDocumentCommand(1L, true, null);
      final MemberSubscription result = service.reviewDocument(command);

      assertThat(result.getPaymentStatus()).isEqualTo(SubscriptionPaymentStatus.REVIEWED);
    }

    @Test
    void shouldRejectAndTransitionBackToNotPaid() {
      subscription.setPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW);
      when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
      when(subscriptionRepository.save(any(MemberSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

      final var command = new ReviewPaymentDocumentCommand(1L, false, "Amount does not match");
      final MemberSubscription result = service.reviewDocument(command);

      assertThat(result.getPaymentStatus()).isEqualTo(SubscriptionPaymentStatus.NOT_PAID);
    }

    @Test
    void shouldRejectWhenStatusNotInReview() {
      subscription.setPaymentStatus(SubscriptionPaymentStatus.NOT_PAID);
      when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));

      final var command = new ReviewPaymentDocumentCommand(1L, true, null);

      assertThatThrownBy(() -> service.reviewDocument(command))
          .isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("IN_REVIEW");
    }

    @Test
    void shouldRequireReasonWhenRejecting() {
      subscription.setPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW);
      when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));

      final var command = new ReviewPaymentDocumentCommand(1L, false, null);

      assertThatThrownBy(() -> service.reviewDocument(command))
          .isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("reason is required");
    }
  }

  @Nested
  class FindBySubscription {

    @Test
    void shouldReturnDocumentsForSubscription() {
      final PaymentDocument doc = PaymentDocument.builder().id(10L).fileName("test.pdf").build();
      when(paymentDocumentRepository.findByMemberSubscriptionId(1L)).thenReturn(List.of(doc));

      final List<PaymentDocument> result = service.findBySubscription(1L);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getFileName()).isEqualTo("test.pdf");
    }
  }
}
