package at.mavila.dbchatbox.infrastructure.scheduling;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.member.MemberGdprService;
import at.mavila.dbchatbox.domain.club.member.MemberRepository;
import at.mavila.dbchatbox.domain.club.member.MemberService;
import at.mavila.dbchatbox.domain.club.member.MemberStatusHistoryRepository;
import at.mavila.dbchatbox.domain.club.member.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job that automatically purges (anonymizes) DELETED members whose retention period has expired.
 *
 * <p>
 * Runs according to the cron expression in {@code app.gdpr.purge-cron}. Only processes members whose DELETED status is
 * older than {@code app.gdpr.retention-days}.
 * </p>
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class GdprPurgeJob {

  private final MemberRepository memberRepository;
  private final MemberStatusHistoryRepository statusHistoryRepository;
  private final MemberGdprService memberGdprService;
  private final MemberService memberService;

  @Value("${app.gdpr.retention-days:30}")
  private int retentionDays;

  @Scheduled(cron = "${app.gdpr.purge-cron:0 0 2 * * *}")
  public void purgeExpiredDeletedMembers() {
    log.info("GDPR purge job started, retention-days={}", retentionDays);
    final LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
    int purged = 0;

    // Find all members with DELETED status older than cutoff
    final List<Member> allMembers = memberRepository.findAll();
    for (final Member member : allMembers) {
      final Status currentStatus = memberService.getCurrentStatus(member);
      if (currentStatus != Status.DELETED) {
        continue;
      }

      // Check if the DELETED status was set before the cutoff
      final var latestHistory = statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(member.getId());
      if (latestHistory.isPresent() && latestHistory.get().getStatus() == Status.DELETED
          && latestHistory.get().getChangedAt().isBefore(cutoff)) {

        // Check if already anonymized (idempotent)
        if (member.getFirstName().equals("DELETED")) {
          continue;
        }

        try {
          memberGdprService.deleteMember(member.getId());
          purged++;
        } catch (final Exception e) {
          log.error("Failed to purge member {}: {}", member.getId(), e.getMessage());
        }
      }
    }

    log.info("GDPR purge job completed, purged {} members", purged);
  }
}
