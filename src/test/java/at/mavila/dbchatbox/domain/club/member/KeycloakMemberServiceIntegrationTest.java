package at.mavila.dbchatbox.domain.club.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import at.mavila.dbchatbox.TenantAwareIntegrationTest;
import at.mavila.dbchatbox.infrastructure.security.KeycloakAdminClient;

/**
 * Verifies {@link KeycloakMemberService#findAll} reads members from the Keycloak Admin client for
 * the current tenant's realm and applies the DB-derived current-status filter.
 */
class KeycloakMemberServiceIntegrationTest extends TenantAwareIntegrationTest {

  /** Realm of the WAT Simmering fixture tenant (id = 1) seeded by V7. */
  private static final String REALM = "wat-simmering";

  @Autowired
  private KeycloakMemberService keycloakMemberService;

  @MockitoBean
  private KeycloakAdminClient keycloakAdminClient;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private MemberStatusHistoryRepository statusHistoryRepository;

  private static MemberView view(final long id) {
    return new MemberView(id, "kc-" + id, "First" + id, "Last", "m%d@test.com".formatted(id),
        null, LocalDate.of(2024, 1, 1), null, OffsetDateTime.now(), OffsetDateTime.now());
  }

  @Test
  void shouldReturnAllMembersWhenNoFilter() {
    when(keycloakAdminClient.listMemberViews(REALM)).thenReturn(List.of(view(700L), view(701L)));

    assertThat(keycloakMemberService.findAll(null)).hasSize(2);
  }

  @Test
  void shouldFilterByCurrentStatus() {
    when(keycloakAdminClient.listMemberViews(REALM)).thenReturn(List.of(view(700L), view(701L)));

    // 700 has no history → defaults to ACTIVE; 701 is explicitly INACTIVE.
    final Member stub701 = memberRepository.saveAndFlush(
        Member.builder().id(701L).keycloakSubject("kc-701").build());
    statusHistoryRepository.saveAndFlush(MemberStatusHistory.builder()
        .member(stub701).status(Status.INACTIVE).changedAt(LocalDateTime.now()).build());

    assertThat(keycloakMemberService.findAll(Status.ACTIVE))
        .extracting(MemberView::id).containsExactly(700L);
    assertThat(keycloakMemberService.findAll(Status.INACTIVE))
        .extracting(MemberView::id).containsExactly(701L);
  }
}
