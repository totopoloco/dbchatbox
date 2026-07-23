package at.mavila.dbchatbox.domain.club.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.exception.DuplicateEmailException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;

@ExtendWith(MockitoExtension.class)
class TrainerServiceTest {

  @Mock
  private TrainerRepository trainerRepository;
  @Mock
  private TrainerSettingsRepository trainerSettingsRepository;
  @Mock
  private CommandValidator commandValidator;
  @Mock
  private TenantScopedFinder tenantScopedFinder;

  @InjectMocks
  private TrainerService service;

  @BeforeEach
  void setUpTenant() {
    TenantContext.setTenantId(1L);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Nested
  class CreateTrainer {

    @Test
    void shouldCreateTrainerAndSettings() {
      when(trainerRepository.existsByEmailAndTenantId("karl@example.com", 1L)).thenReturn(false);
      when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> {
        final Trainer t = inv.getArgument(0);
        t.setId(1L);
        return t;
      });
      when(trainerSettingsRepository.save(any(TrainerSettings.class))).thenAnswer(inv -> inv.getArgument(0));

      final Trainer result = service.createTrainer(new CreateTrainerCommand("Karl", "Weber", "karl@example.com",
          "+431234", BigDecimal.valueOf(45), TrainerPaymentMode.PER_SESSION, false));

      assertThat(result.getFirstName()).isEqualTo("Karl");
      assertThat(result.getEmail()).isEqualTo("karl@example.com");
      verify(trainerSettingsRepository).save(any(TrainerSettings.class));
    }

    @Test
    void shouldCreateSettingsWithDefaultAutoApproveWhenNull() {
      when(trainerRepository.existsByEmailAndTenantId("eva@example.com", 1L)).thenReturn(false);
      when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> {
        final Trainer t = inv.getArgument(0);
        t.setId(2L);
        return t;
      });
      when(trainerSettingsRepository.save(any(TrainerSettings.class))).thenAnswer(inv -> {
        final TrainerSettings s = inv.getArgument(0);
        assertThat(s.getAutoApproveHours()).isFalse();
        return s;
      });

      service.createTrainer(new CreateTrainerCommand("Eva", "Gruber", "eva@example.com", null, BigDecimal.valueOf(60),
          TrainerPaymentMode.MONTHLY, null));
    }

    @Test
    void shouldRejectDuplicateEmail() {
      when(trainerRepository.existsByEmailAndTenantId("karl@example.com", 1L)).thenReturn(true);

      assertThatThrownBy(() -> service.createTrainer(new CreateTrainerCommand("Karl", "Weber", "karl@example.com", null,
          BigDecimal.valueOf(45), TrainerPaymentMode.PER_SESSION, false))).isInstanceOf(DuplicateEmailException.class);
    }
  }

  @Nested
  class UpdateTrainer {

    @Test
    void shouldUpdateContactDetails() {
      final Trainer trainer = Trainer.builder().id(1L).firstName("Karl").lastName("Weber").email("karl@example.com")
          .build();
      when(tenantScopedFinder.findById(trainerRepository, 1L)).thenReturn(Optional.of(trainer));
      when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

      final Trainer result = service.updateTrainer(1L, new UpdateTrainerCommand("Karl-Heinz", null, null, "+43999"));

      assertThat(result.getFirstName()).isEqualTo("Karl-Heinz");
      assertThat(result.getLastName()).isEqualTo("Weber");
      assertThat(result.getPhoneNumber()).isEqualTo("+43999");
    }

    @Test
    void shouldRejectDuplicateEmailOnUpdate() {
      final Trainer trainer = Trainer.builder().id(1L).firstName("Karl").lastName("Weber").email("karl@example.com")
          .build();
      when(tenantScopedFinder.findById(trainerRepository, 1L)).thenReturn(Optional.of(trainer));
      when(trainerRepository.existsByEmailAndIdNotAndTenantId("taken@example.com", 1L, 1L)).thenReturn(true);

      assertThatThrownBy(
          () -> service.updateTrainer(1L, new UpdateTrainerCommand(null, null, "taken@example.com", null)))
          .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void shouldThrowWhenTrainerNotFound() {
      assertThatThrownBy(() -> service.updateTrainer(999L, new UpdateTrainerCommand("X", null, null, null)))
          .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Trainer");
    }
  }

  @Nested
  class UpdateTrainerSettings {

    @Test
    void shouldUpdateHourlyRate() {
      final Trainer trainer = Trainer.builder().id(1L).build();
      final TrainerSettings settings = TrainerSettings.builder().id(1001L).trainer(trainer)
          .hourlyRate(BigDecimal.valueOf(50)).paymentMode(TrainerPaymentMode.PER_SESSION).autoApproveHours(false)
          .build();
      when(tenantScopedFinder.findById(trainerRepository, 1L)).thenReturn(Optional.of(trainer));
      when(trainerSettingsRepository.findByTrainerId(1L)).thenReturn(Optional.of(settings));
      when(trainerSettingsRepository.save(any(TrainerSettings.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerSettings result = service.updateTrainerSettings(1L,
          new UpdateTrainerSettingsCommand(BigDecimal.valueOf(65), null, null));

      assertThat(result.getHourlyRate()).isEqualByComparingTo(BigDecimal.valueOf(65));
      assertThat(result.getPaymentMode()).isEqualTo(TrainerPaymentMode.PER_SESSION);
    }

    @Test
    void shouldUpdatePaymentModeAndAutoApprove() {
      final Trainer trainer = Trainer.builder().id(1L).build();
      final TrainerSettings settings = TrainerSettings.builder().id(1001L).trainer(trainer)
          .hourlyRate(BigDecimal.valueOf(50)).paymentMode(TrainerPaymentMode.PER_SESSION).autoApproveHours(false)
          .build();
      when(tenantScopedFinder.findById(trainerRepository, 1L)).thenReturn(Optional.of(trainer));
      when(trainerSettingsRepository.findByTrainerId(1L)).thenReturn(Optional.of(settings));
      when(trainerSettingsRepository.save(any(TrainerSettings.class))).thenAnswer(inv -> inv.getArgument(0));

      final TrainerSettings result = service.updateTrainerSettings(1L,
          new UpdateTrainerSettingsCommand(null, TrainerPaymentMode.MONTHLY, true));

      assertThat(result.getPaymentMode()).isEqualTo(TrainerPaymentMode.MONTHLY);
      assertThat(result.getAutoApproveHours()).isTrue();
    }

    @Test
    void shouldThrowWhenSettingsNotFound() {
      assertThatThrownBy(() -> service.updateTrainerSettings(999L,
          new UpdateTrainerSettingsCommand(BigDecimal.valueOf(50), null, null)))
          .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Trainer");
    }
  }

  @Nested
  class FindSettingsByTrainerId {

    @Test
    void shouldReturnSettings() {
      final Trainer trainer = Trainer.builder().id(1L).build();
      final TrainerSettings settings = TrainerSettings.builder().id(1001L).trainer(trainer)
          .hourlyRate(BigDecimal.valueOf(50)).paymentMode(TrainerPaymentMode.PER_SESSION).autoApproveHours(false)
          .build();
      when(tenantScopedFinder.findById(trainerRepository, 1L)).thenReturn(Optional.of(trainer));
      when(trainerSettingsRepository.findByTrainerId(1L)).thenReturn(Optional.of(settings));

      final TrainerSettings result = service.findSettingsByTrainerId(1L);

      assertThat(result.getHourlyRate()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void shouldThrowWhenNotFound() {
      assertThatThrownBy(() -> service.findSettingsByTrainerId(999L)).isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
