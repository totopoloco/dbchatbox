package at.mavila.dbchatbox.infrastructure.web.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class MemberControllerIntegrationTest {

  @Autowired
  private HttpGraphQlTester graphQlTester;

  @Test
  void shouldCreateAndRetrieveMember() {
    // Create a member
    final var createResult = graphQlTester.document("""
        mutation {
            createMember(input: {
                firstName: "John"
                lastName: "Doe"
                email: "john.doe@test.com"
                memberSince: "2024-01-01"
            }) {
                id
                firstName
                lastName
                email
                currentStatus
            }
        }
        """).execute();

    createResult.path("createMember.firstName").entity(String.class).isEqualTo("John");
    createResult.path("createMember.lastName").entity(String.class).isEqualTo("Doe");
    createResult.path("createMember.currentStatus").entity(String.class).isEqualTo("ACTIVE");

    final String memberId = createResult.path("createMember.id").entity(String.class).get();
    assertThat(memberId).isNotBlank();

    // Query by ID
    graphQlTester.document("""
        query($id: ID!) {
            memberById(id: $id) {
                firstName
                lastName
                email
                currentStatus
            }
        }
        """).variable("id", memberId).execute().path("memberById.firstName").entity(String.class).isEqualTo("John")
        .path("memberById.currentStatus").entity(String.class).isEqualTo("ACTIVE");
  }

  @Test
  void shouldChangeStatusAndQueryHistory() {
    // Create
    final String memberId = graphQlTester.document("""
        mutation {
            createMember(input: {
                firstName: "Jane"
                lastName: "Smith"
                email: "jane.smith@test.com"
                memberSince: "2024-03-01"
            }) { id }
        }
        """).execute().path("createMember.id").entity(String.class).get();

    // Change status
    graphQlTester.document("""
        mutation($mid: ID!) {
            changeMemberStatus(input: {
                memberId: $mid
                status: "INACTIVE"
                reason: "Requested leave"
            }) { status reason }
        }
        """).variable("mid", memberId).execute().path("changeMemberStatus.status").entity(String.class)
        .isEqualTo("INACTIVE");

    // Query history
    graphQlTester.document("""
        query($mid: ID!) {
            memberStatusHistory(memberId: $mid) {
                status
                reason
            }
        }
        """).variable("mid", memberId).execute().path("memberStatusHistory").entityList(Object.class)
        .satisfies(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(2));
  }

  @Test
  void shouldRejectDuplicateEmail() {
    graphQlTester.document("""
        mutation {
            createMember(input: {
                firstName: "Unique"
                lastName: "User"
                email: "unique@test.com"
                memberSince: "2024-01-01"
            }) { id }
        }
        """).execute();

    graphQlTester.document("""
        mutation {
            createMember(input: {
                firstName: "Another"
                lastName: "User"
                email: "unique@test.com"
                memberSince: "2024-01-01"
            }) { id }
        }
        """).execute().errors().satisfy(errors -> assertThat(errors).isNotEmpty());
  }

  @Test
  void shouldListMembers() {
    graphQlTester.document("""
        query {
            members {
                id
                firstName
                currentStatus
            }
        }
        """).execute().path("members").entityList(Object.class).satisfies(list -> assertThat(list).isNotNull());
  }
}
