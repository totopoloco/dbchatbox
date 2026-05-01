package at.mavila.dbchatbox.infrastructure.scheduling;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.club.member.MemberGdprService;
import at.mavila.dbchatbox.domain.club.member.MemberRepository;
import at.mavila.dbchatbox.domain.club.member.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job that automatically purges (anonymizes) DELETED members whose
 * retention period has expired.
 *
 * <p>
 * Runs according to the cron expression in {@code app.gdpr.purge-cron}. Only
 * processes members whose latest status entry is {@link Status#DELETED} and
 * whose DELETED timestamp is older than {@code app.gdpr.retention-days}.
 * </p>
 *
 * <p>
 * The candidate set is computed by a single repository query
 * ({@link MemberRepository#findGdprPurgeCandidateIds}) — no per-member
 * status / history lookup, and the query already excludes already-anonymized
 * rows so the job stays idempotent and cheap to re-run nightly even after
 * many years of accumulated DELETED records.
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
  private final MemberGdprService memberGdprService;

  @Value("${app.gdpr.retention-days:30}")
  private int retentionDays;

  @Scheduled(cron = "${app.gdpr.purge-cron:0 0 2 * * *}")
  public void purgeExpiredDeletedMembers() {
    log.info("GDPR purge job started, retention-days={}", retentionDays);
    final LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

    final List<Long> candidateIds = memberRepository.findGdprPurgeCandidateIds(
        Status.DELETED, cutoff, MemberGdprService.DELETED_NAME);

    int purged = 0;
    for (final Long memberId : candidateIds) {
      try {
        memberGdprService.deleteMember(memberId);
        purged++;
      } catch (final Exception e) {
        log.error("Failed to purge member {}: {}", memberId, e.getMessage());
      }
    }

    log.info("GDPR purge job completed, candidates={} purged={}", candidateIds.size(), purged);
  }
}
