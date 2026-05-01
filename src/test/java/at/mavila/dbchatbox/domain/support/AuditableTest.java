package at.mavila.dbchatbox.domain.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.member.CreateMemberCommand;
import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.member.MemberRepository;
import at.mavila.dbchatbox.domain.club.member.MemberService;

/**
 * Verifies that the {@link Auditable} JPA lifecycle callbacks populate
 * {@code createdAt} and {@code updatedAt} automatically and that
 * {@code createdAt} is immutable after an update.
 */
@SpringBootTest
@Transactional
class AuditableTest {

  @Autowired
  private MemberService memberService;

  @Autowired
  private MemberRepository memberRepository;

  @Nested
  class OnInsert {

    @Test
    void testCreatedAtAndUpdatedAt_populatedAfterPersist() {
      final LocalDateTime before = LocalDateTime.now().minusSeconds(1);

      final Member member = memberService.createMember(
          new CreateMemberCommand("Jane", "Doe", "jane.audit@example.com",
              null, LocalDate.of(2024, 1, 1), null));

      assertThat(member.getCreatedAt()).isNotNull().isAfter(before);
      assertThat(member.getUpdatedAt()).isNotNull().isAfter(before);
    }
  }

  @Nested
  class OnUpdate {

    @Test
    void testUpdatedAt_advancesAfterUpdate() {
      final Member saved = memberService.createMember(
          new CreateMemberCommand("Jane", "Doe", "jane.update@example.com",
              null, LocalDate.of(2024, 1, 1), null));

      final LocalDateTime createdAt = saved.getCreatedAt();
      final LocalDateTime updatedAtFirst = saved.getUpdatedAt();

      // Flush to DB so the next save triggers @PreUpdate (not a second @PrePersist)
      memberRepository.flush();

      saved.setFirstName("Janet");
      final Member updated = memberRepository.saveAndFlush(saved);

      assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
      assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updatedAtFirst);
    }

    @Test
    void testCreatedAt_unchangedAfterUpdate() {
      final Member saved = memberService.createMember(
          new CreateMemberCommand("Jane", "Doe", "jane.immutable@example.com",
              null, LocalDate.of(2024, 1, 1), null));

      final LocalDateTime originalCreatedAt = saved.getCreatedAt();

      memberRepository.flush();
      saved.setLastName("Smith");
      final Member updated = memberRepository.saveAndFlush(saved);

      assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
    }
  }
}
