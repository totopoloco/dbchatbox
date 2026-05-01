package at.mavila.dbchatbox.domain.club.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.exception.DuplicateNameException;
import at.mavila.dbchatbox.domain.club.exception.InvalidStatusTransitionException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.notification.NotificationService;
import at.mavila.dbchatbox.domain.club.training.Session;
import at.mavila.dbchatbox.domain.club.training.SessionRepository;
import at.mavila.dbchatbox.domain.support.CommandValidator;

@ExtendWith(MockitoExtension.class)
class MembershipTypeServiceTest {

  @Mock
  private MembershipTypeRepository membershipTypeRepository;

  @Mock
  private SessionRepository sessionRepository;

  @Mock
  private CommandValidator commandValidator;

  @Mock
  private NotificationService notificationService;

  @InjectMocks
  private MembershipTypeService service;

  @Nested
  class Create {

    @Test
    void shouldCreateInDraftStatus() {
      when(membershipTypeRepository.existsByName("Gold")).thenReturn(false);
      when(membershipTypeRepository.save(any(MembershipType.class))).thenAnswer(inv -> inv.getArgument(0));

      final MembershipType result = service.create(
          new CreateMembershipTypeCommand("Gold", "Gold plan", BigDecimal.valueOf(99.99), 1, Unit.MONTHS, false, null));

      assertThat(result.getStatus()).isEqualTo(MembershipTypeStatus.DRAFT);
      assertThat(result.getName()).isEqualTo("Gold");
    }

    @Test
    void shouldRejectDuplicateName() {
      when(membershipTypeRepository.existsByName("Gold")).thenReturn(true);

      assertThatThrownBy(
          () -> service.create(new CreateMembershipTypeCommand("Gold", "Desc", BigDecimal.TEN, 1, Unit.MONTHS, false, null)))
          .isInstanceOf(DuplicateNameException.class);
    }

    @Test
    void shouldSetGracePeriodDaysWhenProvided() {
      when(membershipTypeRepository.existsByName("Silver")).thenReturn(false);
      when(membershipTypeRepository.save(any(MembershipType.class))).thenAnswer(inv -> inv.getArgument(0));

      final MembershipType result = service.create(
          new CreateMembershipTypeCommand("Silver", "Silver plan", BigDecimal.valueOf(49.99), 1, Unit.MONTHS, false, 14));

      assertThat(result.getGracePeriodDays()).isEqualTo(14);
    }

    @Test
    void shouldDefaultGracePeriodDaysTo30WhenNull() {
      when(membershipTypeRepository.existsByName("Bronze")).thenReturn(false);
      when(membershipTypeRepository.save(any(MembershipType.class))).thenAnswer(inv -> inv.getArgument(0));

      final MembershipType result = service.create(
          new CreateMembershipTypeCommand("Bronze", "Bronze plan", BigDecimal.valueOf(29.99), 1, Unit.MONTHS, false, null));

      assertThat(result.getGracePeriodDays()).isEqualTo(30);
    }
  }

  @Nested
  class ChangeStatus {

    @Test
    void shouldAllowDraftToActive() {
      final MembershipType type = MembershipType.builder().id(1L).name("Gold").status(MembershipTypeStatus.DRAFT)
          .build();
      when(membershipTypeRepository.findById(1L)).thenReturn(Optional.of(type));
      when(membershipTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      final MembershipType result = service.changeStatus(1L, MembershipTypeStatus.ACTIVE);

      assertThat(result.getStatus()).isEqualTo(MembershipTypeStatus.ACTIVE);
    }

    @Test
    void shouldNotifyWhenTransitioningFromDraftToActive() {
      final MembershipType type = MembershipType.builder().id(1L).name("Gold").status(MembershipTypeStatus.DRAFT)
          .build();
      when(membershipTypeRepository.findById(1L)).thenReturn(Optional.of(type));
      when(membershipTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      service.changeStatus(1L, MembershipTypeStatus.ACTIVE);

      verify(notificationService).notifyMembershipTypePublished(any(MembershipType.class));
    }

    @Test
    void shouldRejectActiveToDeleted() {
      final MembershipType type = MembershipType.builder().id(1L).name("Gold").status(MembershipTypeStatus.ACTIVE)
          .build();
      when(membershipTypeRepository.findById(1L)).thenReturn(Optional.of(type));

      assertThatThrownBy(() -> service.changeStatus(1L, MembershipTypeStatus.DRAFT))
          .isInstanceOf(InvalidStatusTransitionException.class);
    }
  }

  @Nested
  class AssignSession {

    @Test
    void shouldLinkSessionToMembershipType() {
      final MembershipType type = MembershipType.builder().id(1L).name("Gold").status(MembershipTypeStatus.ACTIVE)
          .build();
      final Session session = Session.builder().id(10L).name("Yoga").build();

      when(membershipTypeRepository.findById(1L)).thenReturn(Optional.of(type));
      when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
      when(membershipTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      final MembershipType result = service.assignSession(1L, 10L);

      assertThat(result.getSessions()).contains(session);
    }

    @Test
    void shouldThrowWhenSessionNotFound() {
      final MembershipType type = MembershipType.builder().id(1L).name("Gold").build();
      when(membershipTypeRepository.findById(1L)).thenReturn(Optional.of(type));
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.assignSession(1L, 999L)).isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
