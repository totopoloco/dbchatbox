package at.mavila.dbchatbox.domain.club.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.exception.DuplicateEmailException;
import at.mavila.dbchatbox.domain.club.exception.MemberDeletedException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private MemberStatusHistoryRepository statusHistoryRepository;

  @InjectMocks
  private MemberService memberService;

  private Member sampleMember;

  @BeforeEach
  void setUp() {
    sampleMember = Member.builder().id(1L).firstName("John").lastName("Doe").email("john@example.com")
        .memberSince(LocalDate.of(2024, 1, 1)).build();
  }

  @Nested
  class CreateMember {

    @Test
    void shouldCreateMemberAndSetActiveStatus() {
      when(memberRepository.existsByEmail("john@example.com")).thenReturn(false);
      when(memberRepository.save(any(Member.class))).thenReturn(sampleMember);
      when(statusHistoryRepository.save(any(MemberStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));

      final Member result = memberService.createMember(
          new CreateMemberCommand("John", "Doe", "john@example.com", null, LocalDate.of(2024, 1, 1), null));

      assertThat(result).isNotNull();
      assertThat(result.getFirstName()).isEqualTo("John");
      verify(statusHistoryRepository)
          .save(argThat(h -> h.getStatus() == Status.ACTIVE && h.getMember().equals(sampleMember)));
    }

    @Test
    void shouldRejectDuplicateEmail() {
      when(memberRepository.existsByEmail("john@example.com")).thenReturn(true);

      assertThatThrownBy(() -> memberService.createMember(
          new CreateMemberCommand("John", "Doe", "john@example.com", null, LocalDate.of(2024, 1, 1), null)))
          .isInstanceOf(DuplicateEmailException.class).hasMessageContaining("john@example.com");
    }
  }

  @Nested
  class UpdateMember {

    @Test
    void shouldUpdateOnlyProvidedFields() {
      when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
      when(statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(1L)).thenReturn(
          Optional.of(MemberStatusHistory.builder().status(Status.ACTIVE).changedAt(LocalDateTime.now()).build()));
      when(memberRepository.save(any(Member.class))).thenReturn(sampleMember);

      final Member result = memberService.updateMember(1L,
          new UpdateMemberCommand("Jane", null, null, null, null, null));

      assertThat(result).isNotNull();
      verify(memberRepository).save(argThat(m -> m.getFirstName().equals("Jane")));
    }

    @Test
    void shouldRejectUpdateOfDeletedMember() {
      when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
      when(statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(1L)).thenReturn(
          Optional.of(MemberStatusHistory.builder().status(Status.DELETED).changedAt(LocalDateTime.now()).build()));

      assertThatThrownBy(
          () -> memberService.updateMember(1L, new UpdateMemberCommand("Jane", null, null, null, null, null)))
          .isInstanceOf(MemberDeletedException.class);
    }

    @Test
    void shouldRejectDuplicateEmailOnUpdate() {
      when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
      when(statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(1L)).thenReturn(
          Optional.of(MemberStatusHistory.builder().status(Status.ACTIVE).changedAt(LocalDateTime.now()).build()));
      when(memberRepository.existsByEmailAndIdNot("other@example.com", 1L)).thenReturn(true);

      assertThatThrownBy(() -> memberService.updateMember(1L,
          new UpdateMemberCommand(null, null, "other@example.com", null, null, null)))
          .isInstanceOf(DuplicateEmailException.class);
    }
  }

  @Nested
  class ChangeMemberStatus {

    @Test
    void shouldCreateStatusEntry() {
      when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
      when(statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(1L)).thenReturn(
          Optional.of(MemberStatusHistory.builder().status(Status.ACTIVE).changedAt(LocalDateTime.now()).build()));
      when(statusHistoryRepository.save(any(MemberStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));

      final MemberStatusHistory result = memberService.changeMemberStatus(1L, Status.INACTIVE, "Taking a break");

      assertThat(result.getStatus()).isEqualTo(Status.INACTIVE);
      assertThat(result.getReason()).isEqualTo("Taking a break");
    }
  }

  @Nested
  class FindById {

    @Test
    void shouldThrowWhenNotFound() {
      when(memberRepository.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> memberService.findById(999L)).isInstanceOf(MemberNotFoundException.class);
    }
  }

  @Nested
  class FindAll {

    @Test
    void shouldReturnAllMembersWhenNoFilter() {
      when(memberRepository.findAll()).thenReturn(List.of(sampleMember));

      final List<Member> result = memberService.findAll(null);

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldFilterByStatus() {
      when(memberRepository.findAll()).thenReturn(List.of(sampleMember));
      when(statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(1L)).thenReturn(
          Optional.of(MemberStatusHistory.builder().status(Status.ACTIVE).changedAt(LocalDateTime.now()).build()));

      assertThat(memberService.findAll(Status.ACTIVE)).hasSize(1);
      assertThat(memberService.findAll(Status.INACTIVE)).isEmpty();
    }
  }
}
