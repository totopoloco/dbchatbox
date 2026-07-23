package at.mavila.dbchatbox.infrastructure.web.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import at.mavila.dbchatbox.TenantAwareIntegrationTest;
import at.mavila.dbchatbox.domain.club.member.KeycloakMemberService;
import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.member.MemberRepository;
import at.mavila.dbchatbox.domain.club.member.MemberView;
import at.mavila.dbchatbox.domain.club.member.Status;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeRepository;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeStatus;
import at.mavila.dbchatbox.domain.club.membership.Unit;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionRepository;
import at.mavila.dbchatbox.domain.club.subscription.SubscriptionPaymentStatus;

/**
 * GraphQL wiring tests for {@link MemberController}. Member identity is sourced from Keycloak, so
 * {@link KeycloakMemberService} is mocked; the DB-backed status-history path uses a real persisted
 * {@link Member} stub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureHttpGraphQlTester
class MemberControllerIntegrationTest extends TenantAwareIntegrationTest {

  @Autowired
  private HttpGraphQlTester graphQlTester;

  @MockitoBean
  private KeycloakMemberService keycloakMemberService;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private MemberSubscriptionRepository subscriptionRepository;

  @Autowired
  private MembershipTypeRepository membershipTypeRepository;

  private static MemberView view(final long id, final String first, final String last) {
    return new MemberView(id, "kc-" + id, first, last, "%s@test.com".formatted(first.toLowerCase()),
        null, LocalDate.of(2024, Month.JANUARY, 1), null, OffsetDateTime.now(), OffsetDateTime.now());
  }

  @Test
  void shouldCreateMember() {
    when(keycloakMemberService.createMember(any())).thenReturn(view(100L, "John", "Doe"));
    when(keycloakMemberService.getCurrentStatus(any())).thenReturn(Status.ACTIVE);

    graphQlTester.document("""
        mutation {
            createMember(input: {
                firstName: "John"
                lastName: "Doe"
                email: "john@test.com"
                memberSince: "2024-01-01"
            }) {
                id
                firstName
                lastName
                currentStatus
            }
        }
        """).execute()
        .path("createMember.firstName").entity(String.class).isEqualTo("John")
        .path("createMember.lastName").entity(String.class).isEqualTo("Doe")
        .path("createMember.currentStatus").entity(String.class).isEqualTo("ACTIVE");
  }

  @Test
  void shouldQueryMemberById() {
    when(keycloakMemberService.findById(100L)).thenReturn(view(100L, "John", "Doe"));
    when(keycloakMemberService.getCurrentStatus(any())).thenReturn(Status.ACTIVE);

    graphQlTester.document("""
        query($id: ID!) {
            memberById(id: $id) {
                firstName
                email
                currentStatus
            }
        }
        """).variable("id", "100").execute()
        .path("memberById.firstName").entity(String.class).isEqualTo("John")
        .path("memberById.currentStatus").entity(String.class).isEqualTo("ACTIVE");
  }

  @Test
  void shouldListMembers() {
    when(keycloakMemberService.findAll(null)).thenReturn(java.util.List.of(view(100L, "John", "Doe")));
    when(keycloakMemberService.getCurrentStatus(any())).thenReturn(Status.ACTIVE);

    graphQlTester.document("""
        query {
            members {
                id
                firstName
                currentStatus
            }
        }
        """).execute().path("members[0].firstName").entity(String.class).isEqualTo("John");
  }

  /**
   * Regression: before the fix, Member.subscriptions always threw MemberNotFoundException
   * for members without a DB stub (e.g. Keycloak realm-import fixtures). After the fix the
   * service goes straight to the subscription repository without re-validating via the member table.
   */
  @Test
  void shouldReturnSubscriptionsAsNestedFieldUnderMembers() {
    final long memberId = 859577071316862001L;

    final Member stub = memberRepository.saveAndFlush(
        Member.builder().id(memberId).keycloakSubject("kc-anna").build());

    final MembershipType type = membershipTypeRepository.saveAndFlush(
        MembershipType.builder()
            .name("Annual")
            .price(BigDecimal.valueOf(120))
            .duration(12)
            .unit(Unit.MONTHS)
            .gracePeriodDays(30)
            .status(MembershipTypeStatus.ACTIVE)
            .build());

    subscriptionRepository.saveAndFlush(
        MemberSubscription.builder()
            .member(stub)
            .membershipType(type)
            .startDate(LocalDate.of(2024, Month.JANUARY, 1))
            .endDate(LocalDate.of(2025, Month.JANUARY, 1))
            .agreedPrice(BigDecimal.valueOf(120))
            .paymentStatus(SubscriptionPaymentStatus.NOT_PAID)
            .build());

    when(keycloakMemberService.findAll(null)).thenReturn(List.of(view(memberId, "Anna", "Test")));
    when(keycloakMemberService.getCurrentStatus(any())).thenReturn(Status.ACTIVE);

    graphQlTester.document("""
        query {
            members {
                id
                subscriptions(active: false) {
                    id
                    agreedPrice
                }
            }
        }
        """).execute()
        .path("members[0].subscriptions").entityList(Object.class)
        .satisfies(list -> assertThat(list).hasSize(1));
  }

  /**
   * Regression: standalone memberSubscriptions query hit the same MemberNotFoundException bug.
   * After the fix the controller validates via Keycloak (mocked) and returns subscriptions from DB.
   */
  @Test
  void shouldReturnSubscriptionsViaStandaloneQuery() {
    final long memberId = 700L;

    final Member stub = memberRepository.saveAndFlush(
        Member.builder().id(memberId).keycloakSubject("kc-700").build());

    final MembershipType type = membershipTypeRepository.saveAndFlush(
        MembershipType.builder()
            .name("Monthly")
            .price(BigDecimal.valueOf(15))
            .duration(1)
            .unit(Unit.MONTHS)
            .gracePeriodDays(7)
            .status(MembershipTypeStatus.ACTIVE)
            .build());

    subscriptionRepository.saveAndFlush(
        MemberSubscription.builder()
            .member(stub)
            .membershipType(type)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(1))
            .agreedPrice(BigDecimal.valueOf(15))
            .paymentStatus(SubscriptionPaymentStatus.NOT_PAID)
            .build());

    when(keycloakMemberService.findById(memberId)).thenReturn(view(memberId, "Bob", "Test"));

    graphQlTester.document("""
        query($mid: ID!) {
            memberSubscriptions(memberId: $mid, active: false) {
                id
                agreedPrice
            }
        }
        """).variable("mid", String.valueOf(memberId)).execute()
        .path("memberSubscriptions").entityList(Object.class)
        .satisfies(list -> assertThat(list).hasSize(1));
  }

  @Test
  void shouldChangeStatusAndQueryHistory() {
    // Status transitions are DB-backed; persist a lean stub the real MemberService can resolve.
    memberRepository.saveAndFlush(Member.builder().id(500L).keycloakSubject("kc-500").build());

    graphQlTester.document("""
        mutation($mid: ID!) {
            changeMemberStatus(input: {
                memberId: $mid
                status: "INACTIVE"
                reason: "Requested leave"
            }) { status reason }
        }
        """).variable("mid", "500").execute().path("changeMemberStatus.status").entity(String.class)
        .isEqualTo("INACTIVE");

    graphQlTester.document("""
        query($mid: ID!) {
            memberStatusHistory(memberId: $mid) {
                status
                reason
            }
        }
        """).variable("mid", "500").execute().path("memberStatusHistory").entityList(Object.class)
        .satisfies(list -> assertThat(list).isNotEmpty());
  }
}
