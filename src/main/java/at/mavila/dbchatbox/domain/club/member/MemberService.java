package at.mavila.dbchatbox.domain.club.member;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.MemberDeletedException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for the DB-backed slice of member management — status transitions, status history,
 * and lean stub lookups.
 *
 * <p>
 * Member identity (name, email, contact details) lives in Keycloak and is managed by
 * {@link KeycloakMemberService}; this service owns only what stays in the database: the
 * {@link MemberStatusHistory} timeline and the {@link Member} stub used as an FK target. A member's
 * current status is always derived from the most recent status-history entry.
 * </p>
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MemberService {

  private final MemberRepository memberRepository;
  private final MemberStatusHistoryRepository statusHistoryRepository;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Records a status transition for a member.
   *
   * @param memberId
   *                   the member ID
   * @param status
   *                   the new status
   * @param reason
   *                   optional reason for the transition
   * @return the created status history entry
   * @throws MemberNotFoundException
   *                                   if the member does not exist
   * @throws MemberDeletedException
   *                                   if the member has status DELETED
   */
  public MemberStatusHistory changeMemberStatus(final Long memberId, final Status status, final String reason) {
    final Member member = findByIdOrThrow(memberId);
    guardDeleted(member);
    return createStatusEntry(member, status, reason);
  }

  /**
   * Finds a member stub by ID.
   *
   * @param id
   *             the member ID
   * @return the member stub
   * @throws MemberNotFoundException
   *                                   if the member does not exist
   */
  @Transactional(readOnly = true)
  public Member findById(final Long id) {
    return findByIdOrThrow(id);
  }

  /**
   * Returns the full status history for a member.
   *
   * @param memberId
   *                   the member ID
   * @return all status entries, most recent first
   */
  @Transactional(readOnly = true)
  public List<MemberStatusHistory> getStatusHistory(final Long memberId) {
    findByIdOrThrow(memberId);
    return statusHistoryRepository.findByMemberIdOrderByChangedAtDesc(memberId);
  }

  /**
   * Determines the current status of a member from their latest status history entry.
   *
   * @param member
   *                 the member
   * @return the current status
   */
  @Transactional(readOnly = true)
  public Status getCurrentStatus(final Member member) {
    return getCurrentStatus(member.getId());
  }

  /**
   * Determines the current status of a member from their latest status history entry.
   *
   * @param memberId
   *                   the member ID
   * @return the current status, defaulting to {@code ACTIVE} when no history exists
   */
  @Transactional(readOnly = true)
  public Status getCurrentStatus(final Long memberId) {
    return statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(memberId)
        .map(MemberStatusHistory::getStatus).orElse(Status.ACTIVE);
  }

  private Member findByIdOrThrow(final Long id) {
    return tenantScopedFinder.findById(memberRepository, id)
        .orElseThrow(() -> new MemberNotFoundException(id));
  }

  private void guardDeleted(final Member member) {
    if (getCurrentStatus(member) == Status.DELETED) {
      throw new MemberDeletedException(member.getId());
    }
  }

  private MemberStatusHistory createStatusEntry(final Member member, final Status status, final String reason) {
    final MemberStatusHistory entry = MemberStatusHistory.builder().member(member).status(status)
        .changedAt(LocalDateTime.now()).reason(reason).build();
    return statusHistoryRepository.save(entry);
  }
}
