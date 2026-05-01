package at.mavila.dbchatbox.infrastructure.web.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

/**
 * Integration tests verifying that all {@code DateTime!} schema fields serialize correctly when
 * the backing Java type is {@link java.time.LocalDateTime}.
 *
 * <p>
 * These tests guard against regressions where a {@code DateTime} field is backed by a type that
 * {@link LocalDateTimeScalar} cannot serialize (e.g. if a new field is added with an incompatible
 * type).
 * </p>
 *
 * @since 2026-05-01
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@AutoConfigureWebTestClient(timeout = "30s")
class DateTimeScalarTest {

  @Autowired
  private HttpGraphQlTester graphQlTester;

  @Test
  void testCreatedAt_serializesOnMember() {
    final String memberId = graphQlTester.document("""
        mutation {
            createMember(input: {
                firstName: "DateTime"
                lastName: "ScalarMember"
                email: "datetime.scalar.member@test.com"
                memberSince: "2026-01-01"
            }) { id }
        }
        """).execute().path("createMember.id").entity(String.class).get();

    graphQlTester.document("""
        query($id: ID!) {
            memberById(id: $id) {
                id
                createdAt
            }
        }
        """).variable("id", memberId)
        .execute()
        .errors().satisfy(errors -> assertThat(errors).isEmpty())
        .path("memberById.createdAt").entity(String.class)
        .satisfies(value -> assertThat(value).isNotBlank().contains("T"));
  }

  @Test
  void testChangedAt_serializesOnMemberStatusHistory() {
    final String memberId = graphQlTester.document("""
        mutation {
            createMember(input: {
                firstName: "DateTime"
                lastName: "StatusMember"
                email: "datetime.status.member@test.com"
                memberSince: "2026-01-01"
            }) { id }
        }
        """).execute().path("createMember.id").entity(String.class).get();

    graphQlTester.document("""
        query($mid: ID!) {
            memberStatusHistory(memberId: $mid) {
                status
                changedAt
                createdAt
            }
        }
        """).variable("mid", memberId)
        .execute()
        .errors().satisfy(errors -> assertThat(errors).isEmpty())
        .path("memberStatusHistory[0].changedAt").entity(String.class)
        .satisfies(value -> assertThat(value).isNotBlank().contains("T"));
  }

  @Test
  void testSubmittedAt_serializesOnTrainerLog() {
    // Create trainer
    final String trainerId = graphQlTester.document("""
        mutation {
            createTrainer(input: {
                firstName: "DateTime"
                lastName: "TrainerLog"
                email: "datetime.trainerlog@test.com"
                hourlyRate: "25.00"
                paymentMode: "PER_SESSION"
            }) { id }
        }
        """).execute().path("createTrainer.id").entity(String.class).get();

    // Create session
    final String sessionId = graphQlTester.document("""
        mutation($tid: ID!) {
            createSession(input: {
                name: "DateTime Scalar Session"
                sessionType: "TRAINING"
                dayOfWeek: "MONDAY"
                startTime: "10:00:00"
                endTime: "11:00:00"
                location: "Court A"
                trainerId: $tid
            }) { id }
        }
        """).variable("tid", trainerId).execute().path("createSession.id").entity(String.class).get();

    // Create occurrence (past date so it can be completed)
    final String occurrenceId = graphQlTester.document("""
        mutation($sid: ID!) {
            createSessionOccurrences(input: {
                sessionId: $sid
                startDate: "2026-01-12"
                endDate: "2026-01-12"
            }) { id }
        }
        """).variable("sid", sessionId).execute().path("createSessionOccurrences[0].id").entity(String.class).get();

    // Complete the occurrence
    graphQlTester.document("""
        mutation($oid: ID!) {
            completeSessionOccurrence(id: $oid) { id status }
        }
        """).variable("oid", occurrenceId).execute();

    // Log trainer hours
    graphQlTester.document("""
        mutation($tid: ID!, $oid: ID!) {
            logTrainerHours(input: {
                trainerId: $tid
                sessionOccurrenceId: $oid
                hoursWorked: "2.0"
            }) {
                id
                submittedAt
                reviewedAt
                createdAt
            }
        }
        """).variable("tid", trainerId).variable("oid", occurrenceId)
        .execute()
        .errors().satisfy(errors -> assertThat(errors).isEmpty())
        .path("logTrainerHours.submittedAt").entity(String.class)
        .satisfies(value -> assertThat(value).isNotBlank().contains("T"))
        .path("logTrainerHours.createdAt").entity(String.class)
        .satisfies(value -> assertThat(value).isNotBlank().contains("T"));
  }
}
