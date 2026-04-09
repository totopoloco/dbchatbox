package at.mavila.dbchatbox.domain.club.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link MemberStatusHistory} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface MemberStatusHistoryRepository extends JpaRepository<MemberStatusHistory, Long> {

  /**
   * Finds all status history entries for a member, ordered by change date descending.
   *
   * @param memberId
   *                   the member ID
   * @return the status history entries, most recent first
   */
  List<MemberStatusHistory> findByMemberIdOrderByChangedAtDesc(Long memberId);

  /**
   * Finds the most recent status history entry for a member.
   *
   * @param memberId
   *                   the member ID
   * @return the latest status entry, if any
   */
  Optional<MemberStatusHistory> findFirstByMemberIdOrderByChangedAtDesc(Long memberId);

  /**
   * Finds all status history entries for a member that were changed before a given timestamp.
   *
   * @param memberId
   *                    the member ID
   * @param changedAt
   *                    the cutoff timestamp
   * @return the matching entries
   */
  List<MemberStatusHistory> findByMemberIdAndChangedAtBefore(Long memberId, LocalDateTime changedAt);

  /**
   * Deletes all status history entries for a given member.
   *
   * @param memberId
   *                   the member ID
   */
  void deleteByMemberId(Long memberId);
}
