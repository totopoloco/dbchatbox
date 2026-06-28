package at.mavila.dbchatbox.domain.club.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import at.mavila.dbchatbox.domain.club.member.MemberRepository;
import at.mavila.dbchatbox.domain.club.member.MemberService;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeRepository;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;

@ExtendWith(MockitoExtension.class)
class MemberSubscriptionServiceTest {

  @Mock
  private MemberSubscriptionRepository subscriptionRepository;

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private MembershipTypeRepository membershipTypeRepository;

  @Mock
  private CommandValidator commandValidator;

  @Mock
  private MemberService memberService;

  @Mock
  private TenantScopedFinder tenantScopedFinder;

  @InjectMocks
  private MemberSubscriptionService service;

  @BeforeEach
  void setUpTenant() {
    TenantContext.setTenantId(1L);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Nested
  class FindOverdueSubscriptions {

    @Test
    void shouldReturnSubscriptionsPastGracePeriod() {
      final MembershipType type = MembershipType.builder()
          .id(10L)
          .gracePeriodDays(30)
          .build();

      final MemberSubscription overdue = MemberSubscription.builder()
          .id(1L)
          .startDate(LocalDate.now().minusDays(60))
          .endDate(LocalDate.now().plusMonths(6))
          .agreedPrice(BigDecimal.valueOf(100))
          .membershipType(type)
          .paymentStatus(SubscriptionPaymentStatus.NOT_PAID)
          .build();

      final MemberSubscription withinGrace = MemberSubscription.builder()
          .id(2L)
          .startDate(LocalDate.now().minusDays(10))
          .endDate(LocalDate.now().plusMonths(6))
          .agreedPrice(BigDecimal.valueOf(100))
          .membershipType(type)
          .paymentStatus(SubscriptionPaymentStatus.NOT_PAID)
          .build();

      when(subscriptionRepository.findOverdueCandidates(LocalDate.now(), SubscriptionPaymentStatus.REVIEWED))
          .thenReturn(List.of(overdue, withinGrace));

      final List<MemberSubscription> result = service.findOverdueSubscriptions();

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyWhenNoCandidates() {
      when(subscriptionRepository.findOverdueCandidates(LocalDate.now(), SubscriptionPaymentStatus.REVIEWED))
          .thenReturn(List.of());

      final List<MemberSubscription> result = service.findOverdueSubscriptions();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class FindPendingPaymentReviews {

    @Test
    void shouldReturnSubscriptionsInReview() {
      final MemberSubscription inReview = MemberSubscription.builder()
          .id(1L)
          .paymentStatus(SubscriptionPaymentStatus.IN_REVIEW)
          .build();

      when(subscriptionRepository.findByPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW))
          .thenReturn(List.of(inReview));

      final List<MemberSubscription> result = service.findPendingPaymentReviews();

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getPaymentStatus()).isEqualTo(SubscriptionPaymentStatus.IN_REVIEW);
    }

    @Test
    void shouldReturnEmptyWhenNoPendingReviews() {
      when(subscriptionRepository.findByPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW))
          .thenReturn(List.of());

      final List<MemberSubscription> result = service.findPendingPaymentReviews();

      assertThat(result).isEmpty();
    }
  }
}
