package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscriptionRepository;
import lombok.RequiredArgsConstructor;

/**
 * Handles GDPR right-to-erasure (Art. 17 DSGVO) for members.
 *
 * <p>
 * Anonymizes personal data in-place rather than physically deleting the member row, preserving referential integrity.
 * Payment records are retained for tax compliance (BAO §132).
 * </p>
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MemberGdprService {

  /**
   * Sentinel value written into {@code firstName} / {@code lastName} when a
   * member is anonymized. Promoted to {@code public} so {@code GdprPurgeJob}
   * can share the same constant when querying for "not-yet-anonymized" rows
   * — there is exactly one definition of "anonymized" in this codebase.
   */
  public static final String DELETED_NAME = "DELETED";
  private static final String DELETED_EMAIL_TEMPLATE = "deleted-%d@anonymous.local";

  private final MemberRepository memberRepository;
  private final MemberStatusHistoryRepository statusHistoryRepository;
  private final MemberSubscriptionRepository subscriptionRepository;
  private final MemberService memberService;

  /**
   * Anonymizes a member's personal data and ends all active subscriptions.
   *
   * <p>
   * This operation is idempotent — calling it on an already-deleted member returns success.
   * </p>
   *
   * @param memberId
   *                   the member ID to anonymize
   * @return the fields that were anonymized
   * @throws MemberNotFoundException
   *                                   if the member does not exist
   */
  public DeleteMemberResult deleteMember(final Long memberId) {
    final Member member = memberRepository.findById(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));

    final Status currentStatus = memberService.getCurrentStatus(member);

    // Idempotent: already deleted
    if (currentStatus == Status.DELETED) {
      return new DeleteMemberResult(memberId, LocalDateTime.now(), List.of());
    }

    // Anonymize personal data
    member.setFirstName(DELETED_NAME);
    member.setLastName(DELETED_NAME);
    member.setEmail(DELETED_EMAIL_TEMPLATE.formatted(memberId));
    member.setPhoneNumber(null);
    memberRepository.save(member);

    // Null out reasons in status history (may contain personal data)
    final var history = statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(memberId);
    history.forEach(entry -> entry.setReason(null));
    statusHistoryRepository.saveAll(history);

    // End active subscriptions
    endActiveSubscriptions(memberId);

    // Add DELETED status entry
    final MemberStatusHistory deletedEntry = MemberStatusHistory.builder().member(member).status(Status.DELETED)
        .changedAt(LocalDateTime.now()).reason(null).build();
    statusHistoryRepository.save(deletedEntry);

    return new DeleteMemberResult(memberId, deletedEntry.getChangedAt(),
        List.of("firstName", "lastName", "email", "phoneNumber"));
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
