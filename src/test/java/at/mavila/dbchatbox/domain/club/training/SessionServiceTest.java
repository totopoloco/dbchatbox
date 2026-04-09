package at.mavila.dbchatbox.domain.club.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.OverlapException;
import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.trainer.TrainerPaymentMode;
import at.mavila.dbchatbox.domain.club.trainer.TrainerRepository;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  @Mock
  private SessionRepository sessionRepository;
  @Mock
  private TrainerRepository trainerRepository;

  @InjectMocks
  private SessionService sessionService;

  @Nested
  class CreateSession {

    @Test
    void shouldCreateTrainingSession() {
      final Trainer trainer = Trainer.builder().id(1L).firstName("Bob").lastName("T").email("bob@test.com")
          .hourlyRate(BigDecimal.TEN).paymentMode(TrainerPaymentMode.PER_SESSION).build();

      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(sessionRepository.findByTrainerIdAndDayOfWeek(1L, DayOfWeek.MONDAY)).thenReturn(List.of());
      when(sessionRepository.findByLocationAndDayOfWeek("Room A", DayOfWeek.MONDAY)).thenReturn(List.of());
      when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

      final Session result = sessionService.createSession("Yoga", SessionType.TRAINING, DayOfWeek.MONDAY,
          LocalTime.of(9, 0), LocalTime.of(10, 0), "Room A", 1L);

      assertThat(result.getName()).isEqualTo("Yoga");
      assertThat(result.getTrainer()).isEqualTo(trainer);
    }

    @Test
    void shouldRejectTrainingWithoutTrainer() {
      assertThatThrownBy(() -> sessionService.createSession("Yoga", SessionType.TRAINING, DayOfWeek.MONDAY,
          LocalTime.of(9, 0), LocalTime.of(10, 0), "Room A", null)).isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("trainerId");
    }

    @Test
    void shouldRejectFreeGameWithTrainer() {
      assertThatThrownBy(() -> sessionService.createSession("Open Play", SessionType.FREE_GAME, DayOfWeek.MONDAY,
          LocalTime.of(9, 0), LocalTime.of(10, 0), "Room A", 1L)).isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("FREE_GAME");
    }

    @Test
    void shouldRejectInvalidTimeOrdering() {
      assertThatThrownBy(() -> sessionService.createSession("Yoga", SessionType.FREE_GAME, DayOfWeek.MONDAY,
          LocalTime.of(10, 0), LocalTime.of(9, 0), "Room A", null)).isInstanceOf(InvalidOperationException.class)
          .hasMessageContaining("endTime");
    }

    @Test
    void shouldDetectTrainerOverlap() {
      final Trainer trainer = Trainer.builder().id(1L).firstName("Bob").lastName("T").email("bob@test.com")
          .hourlyRate(BigDecimal.TEN).paymentMode(TrainerPaymentMode.PER_SESSION).build();
      final Session existing = Session.builder().id(99L).startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 30))
          .build();

      when(trainerRepository.findById(1L)).thenReturn(Optional.of(trainer));
      when(sessionRepository.findByTrainerIdAndDayOfWeek(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existing));

      assertThatThrownBy(() -> sessionService.createSession("Pilates", SessionType.TRAINING, DayOfWeek.MONDAY,
          LocalTime.of(9, 0), LocalTime.of(10, 0), "Room B", 1L)).isInstanceOf(OverlapException.class);
    }

    @Test
    void shouldDetectLocationOverlap() {
      final Session existing = Session.builder().id(99L).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
          .build();

      when(sessionRepository.findByLocationAndDayOfWeek("Room A", DayOfWeek.MONDAY)).thenReturn(List.of(existing));

      assertThatThrownBy(() -> sessionService.createSession("Open Play", SessionType.FREE_GAME, DayOfWeek.MONDAY,
          LocalTime.of(9, 15), LocalTime.of(10, 15), "Room A", null)).isInstanceOf(OverlapException.class);
    }
  }

  @Nested
  class FindAvailableTrainers {

    @Test
    void shouldExcludeTrainersWithOverlappingSessions() {
      final Trainer busy = Trainer.builder().id(1L).firstName("Busy").lastName("T").email("busy@t.com")
          .hourlyRate(BigDecimal.TEN).paymentMode(TrainerPaymentMode.PER_SESSION).build();
      final Trainer free = Trainer.builder().id(2L).firstName("Free").lastName("T").email("free@t.com")
          .hourlyRate(BigDecimal.TEN).paymentMode(TrainerPaymentMode.PER_SESSION).build();
      final Session existing = Session.builder().id(99L).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
          .build();

      when(trainerRepository.findAll()).thenReturn(List.of(busy, free));
      when(sessionRepository.findByTrainerIdAndDayOfWeek(1L, DayOfWeek.MONDAY)).thenReturn(List.of(existing));
      when(sessionRepository.findByTrainerIdAndDayOfWeek(2L, DayOfWeek.MONDAY)).thenReturn(List.of());

      final List<Trainer> result = sessionService.findAvailableTrainers(DayOfWeek.MONDAY, LocalTime.of(9, 0),
          LocalTime.of(10, 0));

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getFirstName()).isEqualTo("Free");
    }
  }
}
