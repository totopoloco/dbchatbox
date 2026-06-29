package at.mavila.dbchatbox.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import at.mavila.dbchatbox.domain.club.member.MemberView;

/**
 * Verifies the Keycloak {@code UserRepresentation} JSON → {@link MemberView} attribute mapping
 * (custom attributes, {@code createdTimestamp}) including the missing-optional-attribute case.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakAdminClientTest {

  @Mock
  private BearerTokenHolder bearerTokenHolder;

  private MockRestServiceServer server;
  private KeycloakAdminClient client;

  @BeforeEach
  void setUp() {
    final RestClient.Builder builder = RestClient.builder().baseUrl("http://kc");
    this.server = MockRestServiceServer.bindTo(builder).build();
    this.client = new KeycloakAdminClient(builder.build(), bearerTokenHolder);
    when(bearerTokenHolder.getToken()).thenReturn("test-token");
  }

  @Test
  void shouldMapAllCustomAttributes() {
    server.expect(requestTo(startsWith("http://kc/admin/realms/wat-simmering/users")))
        .andRespond(withSuccess("""
            [
              {
                "id": "kc-uuid-1",
                "username": "anna",
                "firstName": "Anna",
                "lastName": "WAT",
                "email": "anna@wat-simmering.local",
                "enabled": true,
                "createdTimestamp": 1705312800000,
                "attributes": {
                  "memberId":    ["550894081304985601"],
                  "phoneNumber": ["+43 676 2001"],
                  "memberSince": ["2024-01-15"],
                  "memberUntil": ["2026-12-31"],
                  "updatedAt":   ["2026-01-15T10:00:00+01:00"]
                }
              }
            ]
            """, MediaType.APPLICATION_JSON));

    final MemberView view = client.findByMemberId("wat-simmering", 550894081304985601L);

    assertThat(view.id()).isEqualTo(550894081304985601L);
    assertThat(view.keycloakSubject()).isEqualTo("kc-uuid-1");
    assertThat(view.firstName()).isEqualTo("Anna");
    assertThat(view.email()).isEqualTo("anna@wat-simmering.local");
    assertThat(view.phoneNumber()).isEqualTo("+43 676 2001");
    assertThat(view.memberSince()).isEqualTo(LocalDate.of(2024, 1, 15));
    assertThat(view.memberUntil()).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(view.createdAt()).isNotNull();
    assertThat(view.updatedAt()).isNotNull();
  }

  @Test
  void shouldYieldNullsForMissingOptionalAttributes() {
    server.expect(requestTo(startsWith("http://kc/admin/realms/wat-simmering/users")))
        .andRespond(withSuccess("""
            [
              {
                "id": "kc-uuid-2",
                "username": "bob",
                "firstName": "Bob",
                "lastName": "WAT",
                "email": "bob@wat-simmering.local",
                "enabled": true,
                "attributes": {
                  "memberId": ["550894081304985602"]
                }
              }
            ]
            """, MediaType.APPLICATION_JSON));

    final MemberView view = client.findByMemberId("wat-simmering", 550894081304985602L);

    assertThat(view.id()).isEqualTo(550894081304985602L);
    assertThat(view.phoneNumber()).isNull();
    assertThat(view.memberSince()).isNull();
    assertThat(view.memberUntil()).isNull();
    assertThat(view.createdAt()).isNull();
    assertThat(view.updatedAt()).isNull();
  }
}
