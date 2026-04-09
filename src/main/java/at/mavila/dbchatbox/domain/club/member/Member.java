package at.mavila.dbchatbox.domain.club.member;

import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Represents a person's membership in the club — their identity, contact details, and when they joined.
 *
 * <p>
 * Status is <strong>not</strong> stored directly on this entity; it is derived from the most recent
 * {@link MemberStatusHistory} entry. Membership types are managed via {@code MemberSubscription}.
 * </p>
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

  @Id
  @TsidGenerated
  private Long id;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "phone_number")
  private String phoneNumber;

  @Column(name = "member_since", nullable = false)
  private LocalDate memberSince;

  @Column(name = "member_until")
  private LocalDate memberUntil;
}
