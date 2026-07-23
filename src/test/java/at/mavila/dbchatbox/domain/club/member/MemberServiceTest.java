package at.mavila.dbchatbox.domain.club.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.exception.MemberDeletedException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private MemberStatusHistoryRepository statusHistoryRepository;

  @Mock
  private TenantScopedFinder tenantScopedFinder;

  @InjectMocks
  private MemberService memberService;

  private Member sampleMember;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(1L);
    sampleMember = Member.builder().id(1L).keycloakSubject("kc-1").build();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  private void mockCurrentStatus(final Status status) {
    when(statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(1L)).thenReturn(
        Optional.of(MemberStatusHistory.builder().status(status).changedAt(LocalDateTime.now()).build()));
  }

  @Nested
  class ChangeMemberStatus {

    @Test
    void shouldCreateStatusEntry() {
      when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));
      mockCurrentStatus(Status.ACTIVE);
      when(statusHistoryRepository.save(any(MemberStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));

      final MemberStatusHistory result = memberService.changeMemberStatus(1L, Status.INACTIVE, "Taking a break");

      assertThat(result.getStatus()).isEqualTo(Status.INACTIVE);
      assertThat(result.getReason()).isEqualTo("Taking a break");
    }

    @Test
    void shouldRejectStatusChangeForDeletedMember() {
      when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));
      mockCurrentStatus(Status.DELETED);

      assertThatThrownBy(() -> memberService.changeMemberStatus(1L, Status.ACTIVE, "Reinstate"))
          .isInstanceOf(MemberDeletedException.class);
    }
  }

  @Nested
  class FindById {

    @Test
    void shouldReturnMember() {
      when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));

      assertThat(memberService.findById(1L)).isSameAs(sampleMember);
    }

    @Test
    void shouldThrowWhenNotFound() {
      assertThatThrownBy(() -> memberService.findById(999L)).isInstanceOf(MemberNotFoundException.class);
    }
  }

  @Nested
  class StatusHistory {

    @Test
    void shouldReturnHistoryForMember() {
      when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));
      when(statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(1L)).thenReturn(
          List.of(MemberStatusHistory.builder().status(Status.ACTIVE).changedAt(LocalDateTime.now()).build()));

      assertThat(memberService.getStatusHistory(1L)).hasSize(1);
    }
  }

  @Nested
  class CurrentStatus {

    @Test
    void shouldReturnLatestStatus() {
      mockCurrentStatus(Status.INACTIVE);

      assertThat(memberService.getCurrentStatus(sampleMember)).isEqualTo(Status.INACTIVE);
      assertThat(memberService.getCurrentStatus(1L)).isEqualTo(Status.INACTIVE);
    }

    @Test
    void shouldDefaultToActiveWhenNoHistory() {
      when(statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(1L)).thenReturn(Optional.empty());

      assertThat(memberService.getCurrentStatus(1L)).isEqualTo(Status.ACTIVE);
    }
  }
}
