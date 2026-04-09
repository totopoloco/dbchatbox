package at.mavila.dbchatbox.domain.club.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.training.Session;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrence;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceRepository;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceStatus;
import at.mavila.dbchatbox.domain.club.training.SessionType;

@ExtendWith(MockitoExtension.class)
class TrainerLogServiceTest {

  @Mock
  private TrainerLogRepository trainerLogRepository;
  @Mock
  private TrainerRepository trainerRepository;
  @Mock
  private SessionOccurrenceRepository occurrenceRepository;

  @InjectMocks
  private TrainerLogService service;

  private Trainer trainer;
  private Session session;
  private SessionOccurrence occurrence;

  @BeforeEach
  void setUp() {
    trainer = Trainer.builder().id(1L).firstName("Alice").lastName("Smith").email("alice@example.com")
        .hourlyRate(BigDecimal.valueOf(50)).paymentMode(TrainerPaymentMode.PER_SESSION).autoApproveHours(false).build();

    session = Session.builder().id(10L).name("Yoga").sessionType(SessionType.TRAINING).dayOfWeek(DayOfWeek.MONDAY)
        .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).location("Room A").trainer(trainer).build();

    occurrence = SessionOccurrence.builder().id(100L).session(session).date(LocalDate.of(2024, 6, 3))
        .status(SessionOccurrenceStatus.COMPLETED).build();
  }

  @Nested
  class LogTrainerHours {

    @Test
    void shouldCreateApprovedLogEntry() {
      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(occurrenceRepository.findById(100L)).thenReturn(Optional.of(occurrence));
      when(trainerLogRepository.existsByTrainerIdAndSessionOccurrenceId(1L, 100L)).thenReturn(false);
      when(trainerLogRepository.save(any(TrainerLog.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerLog result = service.logTrainerHours(1L, 100L, BigDecimal.valueOf(2), "Good session");

      assertThat(result.getStatus()).isEqualTo(TrainerLogStatus.APPROVED);
      assertThat(result.getHoursWorked()).isEqualByComparingTo(BigDecimal.valueOf(2));
    }

    @Test
    void shouldRejectDuplicate() {
      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(occurrenceRepository.findById(100L)).thenReturn(Optional.of(occurrence));
      when(trainerLogRepository.existsByTrainerIdAndSessionOccurrenceId(1L, 100L)).thenReturn(true);

      assertThatThrownBy(() -> service.logTrainerHours(1L, 100L, BigDecimal.valueOf(2), null))
          .isInstanceOf(InvalidOperationException.class).hasMessageContaining("already exists");
    }

    @Test
    void shouldRejectNonCompletedOccurrence() {
      occurrence.setStatus(SessionOccurrenceStatus.SCHEDULED);
      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(occurrenceRepository.findById(100L)).thenReturn(Optional.of(occurrence));

      assertThatThrownBy(() -> service.logTrainerHours(1L, 100L, BigDecimal.valueOf(2), null))
          .isInstanceOf(InvalidOperationException.class).hasMessageContaining("COMPLETED");
    }

    @Test
    void shouldRejectZeroHours() {
      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(occurrenceRepository.findById(100L)).thenReturn(Optional.of(occurrence));

      assertThatThrownBy(() -> service.logTrainerHours(1L, 100L, BigDecimal.ZERO, null))
          .isInstanceOf(InvalidOperationException.class).hasMessageContaining("positive");
    }
  }

  @Nested
  class SubmitTrainerHours {

    @Test
    void shouldCreatePendingEntryWhenAutoApproveIsFalse() {
      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(occurrenceRepository.findById(100L)).thenReturn(Optional.of(occurrence));
      when(trainerLogRepository.existsByTrainerIdAndSessionOccurrenceId(1L, 100L)).thenReturn(false);
      when(trainerLogRepository.save(any(TrainerLog.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerLog result = service.submitTrainerHours(1L, 100L, BigDecimal.valueOf(1.5), null);

      assertThat(result.getStatus()).isEqualTo(TrainerLogStatus.PENDING);
    }

    @Test
    void shouldAutoApproveWhenTrainerFlagIsSet() {
      trainer.setAutoApproveHours(true);
      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(occurrenceRepository.findById(100L)).thenReturn(Optional.of(occurrence));
      when(trainerLogRepository.existsByTrainerIdAndSessionOccurrenceId(1L, 100L)).thenReturn(false);
      when(trainerLogRepository.save(any(TrainerLog.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerLog result = service.submitTrainerHours(1L, 100L, BigDecimal.valueOf(1.5), null);

      assertThat(result.getStatus()).isEqualTo(TrainerLogStatus.APPROVED);
    }
  }

  @Nested
  class ApproveLog {

    @Test
    void shouldAprovePendingEntry() {
      final TrainerLog log = TrainerLog.builder().id(200L).trainer(trainer).sessionOccurrence(occurrence)
          .hoursWorked(BigDecimal.valueOf(2)).status(TrainerLogStatus.PENDING).build();
      when(trainerLogRepository.findById(200L)).thenReturn(Optional.of(log));
      when(trainerLogRepository.save(any(TrainerLog.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerLog result = service.approveLog(200L);

      assertThat(result.getStatus()).isEqualTo(TrainerLogStatus.APPROVED);
      assertThat(result.getReviewedAt()).isNotNull();
    }

    @Test
    void shouldRejectApprovingNonPending() {
      final TrainerLog log = TrainerLog.builder().id(200L).status(TrainerLogStatus.APPROVED).build();
      when(trainerLogRepository.findById(200L)).thenReturn(Optional.of(log));

      assertThatThrownBy(() -> service.approveLog(200L)).isInstanceOf(InvalidOperationException.class);
    }
  }

  @Nested
  class RejectLog {

    @Test
    void shouldRejectWithReason() {
      final TrainerLog log = TrainerLog.builder().id(200L).trainer(trainer).sessionOccurrence(occurrence)
          .hoursWorked(BigDecimal.valueOf(2)).status(TrainerLogStatus.PENDING).build();
      when(trainerLogRepository.findById(200L)).thenReturn(Optional.of(log));
      when(trainerLogRepository.save(any(TrainerLog.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerLog result = service.rejectLog(200L, "Hours seem too high");

      assertThat(result.getStatus()).isEqualTo(TrainerLogStatus.REJECTED);
      assertThat(result.getRejectionReason()).isEqualTo("Hours seem too high");
    }

    @Test
    void shouldRejectWithoutReason() {
      final TrainerLog log = TrainerLog.builder().id(200L).status(TrainerLogStatus.PENDING).build();
      when(trainerLogRepository.findById(200L)).thenReturn(Optional.of(log));

      assertThatThrownBy(() -> service.rejectLog(200L, "")).isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("reason");
    }
  }

  @Nested
  class ResubmitLog {

    @Test
    void shouldResetToPendingAfterRejection() {
      final TrainerLog log = TrainerLog.builder().id(200L).trainer(trainer).sessionOccurrence(occurrence)
          .hoursWorked(BigDecimal.valueOf(10)).status(TrainerLogStatus.REJECTED).rejectionReason("Too high").build();
      when(trainerLogRepository.findById(200L)).thenReturn(Optional.of(log));
      when(trainerLogRepository.save(any(TrainerLog.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerLog result = service.resubmitLog(200L, BigDecimal.valueOf(2), "Corrected");

      assertThat(result.getStatus()).isEqualTo(TrainerLogStatus.PENDING);
      assertThat(result.getHoursWorked()).isEqualByComparingTo(BigDecimal.valueOf(2));
      assertThat(result.getRejectionReason()).isNull();
    }
  }

  @Nested
  class GetHoursSummary {

    @Test
    void shouldCalculateSummary() {
      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(trainerLogRepository.sumApprovedHoursByTrainerAndDateRange(1L, LocalDate.of(2024, 6, 1),
          LocalDate.of(2024, 6, 30))).thenReturn(BigDecimal.valueOf(20));
      when(trainerLogRepository.countApprovedByTrainerAndDateRange(1L, LocalDate.of(2024, 6, 1),
          LocalDate.of(2024, 6, 30))).thenReturn(10L);

      final var summary = service.getHoursSummary(1L, LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 30));

      assertThat(summary.totalHours()).isEqualByComparingTo(BigDecimal.valueOf(20));
      assertThat(summary.sessionCount()).isEqualTo(10);
      assertThat(summary.totalOwed()).isEqualByComparingTo(BigDecimal.valueOf(1000)); // 20 * 50
    }
  }
}
