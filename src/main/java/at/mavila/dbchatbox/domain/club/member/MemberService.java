package at.mavila.dbchatbox.domain.club.member;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.DuplicateEmailException;
import at.mavila.dbchatbox.domain.club.exception.MemberDeletedException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for member management — registration, updates, status changes, and queries.
 *
 * <p>
 * A member's current status is derived from the most recent {@link MemberStatusHistory} entry, not stored directly on
 * the {@link Member} entity.
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
  private final CommandValidator commandValidator;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Registers a new member and creates an initial ACTIVE status entry.
   *
   * @param command
   *                  the member creation command
   * @return the created member
   * @throws DuplicateEmailException
   *                                   if the email is already in use
   */
  public Member createMember(final CreateMemberCommand command) {
    commandValidator.validate(command);

    if (memberRepository.existsByEmailAndTenantId(command.email(), currentTenantOrThrow())) {
      throw new DuplicateEmailException(command.email());
    }

    final Member member = Member.builder().firstName(command.firstName()).lastName(command.lastName())
        .email(command.email()).phoneNumber(command.phoneNumber()).memberSince(command.memberSince())
        .memberUntil(command.memberUntil()).build();

    final Member saved = memberRepository.save(member);

    createStatusEntry(saved, Status.ACTIVE, "Member registered");

    return saved;
  }

  /**
   * Updates an existing member's details.
   *
   * @param id
   *                  the member ID
   * @param command
   *                  the member update command (null fields are not changed)
   * @return the updated member
   * @throws MemberNotFoundException
   *                                   if the member does not exist
   * @throws MemberDeletedException
   *                                   if the member has been GDPR-deleted
   * @throws DuplicateEmailException
   *                                   if the new email is already in use
   */
  public Member updateMember(final Long id, final UpdateMemberCommand command) {
    final Member member = findByIdOrThrow(id);
    guardDeleted(member);

    if (nonNull(command.firstName())) {
      member.setFirstName(command.firstName());
    }
    if (nonNull(command.lastName())) {
      member.setLastName(command.lastName());
    }
    if (nonNull(command.email()) && !command.email().equals(member.getEmail())) {
      final Long tenantId = currentTenantOrThrow();
      if (memberRepository.existsByEmailAndIdNotAndTenantId(command.email(), id, tenantId)) {
        throw new DuplicateEmailException(command.email());
      }
      member.setEmail(command.email());
    }
    if (nonNull(command.phoneNumber())) {
      member.setPhoneNumber(command.phoneNumber());
    }
    if (nonNull(command.memberSince())) {
      member.setMemberSince(command.memberSince());
    }
    if (nonNull(command.memberUntil())) {
      member.setMemberUntil(command.memberUntil());
    }

    return memberRepository.save(member);
  }

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
   * Finds a member by ID.
   *
   * @param id
   *             the member ID
   * @return the member
   * @throws MemberNotFoundException
   *                                   if the member does not exist
   */
  @Transactional(readOnly = true)
  public Member findById(final Long id) {
    return findByIdOrThrow(id);
  }

  /**
   * Lists all members, optionally filtered by current status.
   *
   * @param status
   *                 the status to filter by (null for all)
   * @return matching members
   */
  @Transactional(readOnly = true)
  public List<Member> findAll(final Status status) {
    if (isNull(status)) {
      return memberRepository.findAllByTenantId(currentTenantOrThrow());
    }

    return memberRepository.findAllByTenantId(currentTenantOrThrow()).stream()
        .filter(member -> getCurrentStatus(member).equals(status)).toList();
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
    return statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(member.getId())
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

  private Long currentTenantOrThrow() {
    final Long t = TenantContext.getTenantId();
    if (isNull(t)) {
      throw new IllegalStateException("No tenant in context");
    }
    return t;
  }
}
