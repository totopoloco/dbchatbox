package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionRepository;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Handles the database side of GDPR right-to-erasure (Art. 17 DSGVO) for members.
 *
 * <p>
 * Personal data itself lives in Keycloak; the interactive {@code deleteMember} mutation scrubs the
 * Keycloak account synchronously (see {@code KeycloakMemberService.anonymizeInKeycloak}). This
 * service performs the DB-side steps: flagging the member row {@code anonymized}, clearing
 * status-history reasons, and ending active subscriptions. Payment records are retained for tax
 * compliance (BAO §132).
 * </p>
 *
 * <p>
 * It must not call Keycloak — it also runs from the {@code GdprPurgeJob} scheduled thread where no
 * request-scoped JWT is available.
 * </p>
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MemberGdprService {

  /**
   * Fields reported as erased by a GDPR deletion: the personal data scrubbed from the Keycloak
   * account (by the interactive mutation handler) plus the DB row's {@code anonymized} flag.
   */
  private static final List<String> ANONYMIZED_FIELDS =
      List.of("keycloak.firstName", "keycloak.lastName", "keycloak.email", "keycloak.phoneNumber",
          "member.anonymized");

  private final MemberRepository memberRepository;
  private final MemberStatusHistoryRepository statusHistoryRepository;
  private final MemberSubscriptionRepository subscriptionRepository;
  private final MemberService memberService;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Performs the DB-side GDPR erasure for a member: flags the row anonymized, clears status-history
   * reasons, ends active subscriptions, and records a {@code DELETED} status entry if the member is
   * not already in that state.
   *
   * <p>
   * Idempotent — calling it on an already-anonymized member returns success without further writes,
   * which also keeps the nightly {@code GdprPurgeJob} from re-processing the same rows.
   * </p>
   *
   * @param memberId
   *                   the member ID to anonymize
   * @return the fields that were anonymized
   * @throws MemberNotFoundException
   *                                   if the member does not exist
   */
  public DeleteMemberResult deleteMember(final Long memberId) {
    final Member member = tenantScopedFinder.findById(memberRepository, memberId)
        .orElseThrow(() -> new MemberNotFoundException(memberId));

    if (member.isAnonymized()) {
      return new DeleteMemberResult(memberId, LocalDateTime.now(), List.of());
    }

    final Status currentStatus = memberService.getCurrentStatus(member);

    member.setAnonymized(true);
    memberRepository.save(member);

    final var history = statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(memberId);
    history.forEach(entry -> entry.setReason(null));
    statusHistoryRepository.saveAll(history);

    endActiveSubscriptions(memberId);

    final LocalDateTime anonymizedAt = recordDeletedStatusIfNeeded(member, currentStatus);
    return new DeleteMemberResult(memberId, anonymizedAt, ANONYMIZED_FIELDS);
  }

  private LocalDateTime recordDeletedStatusIfNeeded(final Member member, final Status currentStatus) {
    if (currentStatus == Status.DELETED) {
      return LocalDateTime.now();
    }
    final MemberStatusHistory deletedEntry = MemberStatusHistory.builder().member(member).status(Status.DELETED)
        .changedAt(LocalDateTime.now()).reason(null).build();
    statusHistoryRepository.save(deletedEntry);
    return deletedEntry.getChangedAt();
  }

  private void endActiveSubscriptions(final Long memberId) {
    final LocalDate today = LocalDate.now();
    final List<MemberSubscription> activeSubscriptions = subscriptionRepository
        .findByMemberIdAndEndDateGreaterThanEqual(memberId, today);

    activeSubscriptions.forEach(sub -> sub.setEndDate(today));
    subscriptionRepository.saveAll(activeSubscriptions);
  }

  /**
   * Result of a GDPR member deletion (anonymization).
   *
   * @param memberId
   *                           the member ID
   * @param anonymizedAt
   *                           when the erasure was performed
   * @param fieldsAnonymized
   *                           list of field names that were anonymized
   */
  public record DeleteMemberResult(Long memberId, LocalDateTime anonymizedAt, List<String> fieldsAnonymized) {
  }
}
