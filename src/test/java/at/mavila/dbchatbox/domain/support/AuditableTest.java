package at.mavila.dbchatbox.domain.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.TenantAwareIntegrationTest;
import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.member.MemberRepository;

/**
 * Verifies that the {@link Auditable} JPA lifecycle callbacks populate
 * {@code createdAt} and {@code updatedAt}
 * automatically and that {@code createdAt} is immutable after an update.
 *
 * <p>Uses the lean {@link Member} stub (the smallest {@code Auditable} entity) persisted directly
 * via the repository — member identity now lives in Keycloak, so there is no DB-side member
 * creation service to go through.</p>
 */
@Transactional
class AuditableTest extends TenantAwareIntegrationTest {

  @Autowired
  private MemberRepository memberRepository;

  @Nested
  class OnInsert {

    @Test
    void testCreatedAtAndUpdatedAt_populatedAfterPersist() {
      final OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

      final Member member = memberRepository.saveAndFlush(
          Member.builder().id(810L).keycloakSubject("kc-audit-insert").build());

      assertThat(member.getCreatedAt()).isNotNull().isAfter(before);
      assertThat(member.getUpdatedAt()).isNotNull().isAfter(before);
    }
  }

  @Nested
  class OnUpdate {

    @Test
    void testUpdatedAt_advancesAfterUpdate() {
      final Member saved = memberRepository.saveAndFlush(
          Member.builder().id(811L).keycloakSubject("kc-audit-update").build());

      final OffsetDateTime createdAt = saved.getCreatedAt();
      final OffsetDateTime updatedAtFirst = saved.getUpdatedAt();

      // Flush to DB so the next save triggers @PreUpdate (not a second @PrePersist)
      memberRepository.flush();

      saved.setKeycloakSubject("kc-audit-update-2");
      final Member updated = memberRepository.saveAndFlush(saved);

      assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
      assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updatedAtFirst);
    }

    @Test
    void testCreatedAt_unchangedAfterUpdate() {
      final Member saved = memberRepository.saveAndFlush(
          Member.builder().id(812L).keycloakSubject("kc-audit-immutable").build());

      final OffsetDateTime originalCreatedAt = saved.getCreatedAt();

      memberRepository.flush();
      saved.setAnonymized(true);
      final Member updated = memberRepository.saveAndFlush(saved);

      assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
    }
  }
}
