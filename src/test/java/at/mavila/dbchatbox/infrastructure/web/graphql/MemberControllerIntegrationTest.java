package at.mavila.dbchatbox.infrastructure.web.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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

  private static MemberView view(final long id, final String first, final String last) {
    return new MemberView(id, "kc-" + id, first, last, "%s@test.com".formatted(first.toLowerCase()),
        null, LocalDate.of(2024, 1, 1), null, OffsetDateTime.now(), OffsetDateTime.now());
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
