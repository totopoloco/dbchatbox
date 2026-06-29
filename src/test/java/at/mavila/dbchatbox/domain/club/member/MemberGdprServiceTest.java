package at.mavila.dbchatbox.domain.club.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionRepository;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;

@ExtendWith(MockitoExtension.class)
class MemberGdprServiceTest {

  @Mock
  private MemberRepository memberRepository;
  @Mock
  private MemberStatusHistoryRepository statusHistoryRepository;
  @Mock
  private MemberSubscriptionRepository subscriptionRepository;
  @Mock
  private MemberService memberService;
  @Mock
  private TenantScopedFinder tenantScopedFinder;

  @InjectMocks
  private MemberGdprService gdprService;

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

  @Test
  void shouldFlagMemberAnonymizedAndRecordDeletedStatus() {
    when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));
    when(memberService.getCurrentStatus(sampleMember)).thenReturn(Status.ACTIVE);
    when(statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(1L)).thenReturn(List.of());
    when(subscriptionRepository.findByMemberIdAndEndDateGreaterThanEqual(anyLong(), any(LocalDate.class)))
        .thenReturn(List.of());
    when(statusHistoryRepository.save(any(MemberStatusHistory.class))).thenAnswer(inv -> {
      MemberStatusHistory h = inv.getArgument(0);
      h.setChangedAt(LocalDateTime.now());
      return h;
    });
    when(memberRepository.save(any(Member.class))).thenReturn(sampleMember);

    final var result = gdprService.deleteMember(1L);

    assertThat(result.memberId()).isEqualTo(1L);
    assertThat(result.fieldsAnonymized()).contains("member.anonymized");
    verify(memberRepository).save(argThat(Member::isAnonymized));
    verify(statusHistoryRepository).save(argThat(h -> h.getStatus() == Status.DELETED));
  }

  @Test
  void shouldBeIdempotentForAlreadyAnonymizedMember() {
    sampleMember.setAnonymized(true);
    when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));

    final var result = gdprService.deleteMember(1L);

    assertThat(result.fieldsAnonymized()).isEmpty();
    verify(memberRepository, never()).save(any());
  }

  @Test
  void shouldNotRecordSecondDeletedEntryWhenAlreadyDeleted() {
    when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));
    when(memberService.getCurrentStatus(sampleMember)).thenReturn(Status.DELETED);
    when(statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(1L)).thenReturn(List.of());
    when(subscriptionRepository.findByMemberIdAndEndDateGreaterThanEqual(anyLong(), any(LocalDate.class)))
        .thenReturn(List.of());
    when(memberRepository.save(any(Member.class))).thenReturn(sampleMember);

    gdprService.deleteMember(1L);

    verify(memberRepository).save(argThat(Member::isAnonymized));
    verify(statusHistoryRepository, never()).save(any());
  }

  @Test
  void shouldEndActiveSubscriptions() {
    final MemberSubscription activeSub = MemberSubscription.builder().id(10L).member(sampleMember)
        .startDate(LocalDate.of(2024, 1, 1)).endDate(LocalDate.of(2025, 12, 31)).agreedPrice(BigDecimal.valueOf(100))
        .build();

    when(tenantScopedFinder.findById(memberRepository, 1L)).thenReturn(Optional.of(sampleMember));
    when(memberService.getCurrentStatus(sampleMember)).thenReturn(Status.ACTIVE);
    when(statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(1L)).thenReturn(List.of());
    when(subscriptionRepository.findByMemberIdAndEndDateGreaterThanEqual(anyLong(), any(LocalDate.class)))
        .thenReturn(List.of(activeSub));
    when(statusHistoryRepository.save(any(MemberStatusHistory.class))).thenAnswer(inv -> {
      MemberStatusHistory h = inv.getArgument(0);
      h.setChangedAt(LocalDateTime.now());
      return h;
    });
    when(memberRepository.save(any(Member.class))).thenReturn(sampleMember);

    gdprService.deleteMember(1L);

    verify(subscriptionRepository).saveAll(argThat(subs -> {
      final var list = (List<MemberSubscription>) subs;
      return list.size() == 1 && !list.getFirst().getEndDate().isAfter(LocalDate.now());
    }));
  }

  @Test
  void shouldThrowWhenMemberNotFound() {
    assertThatThrownBy(() -> gdprService.deleteMember(999L)).isInstanceOf(MemberNotFoundException.class);
  }
}
