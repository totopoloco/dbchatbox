/**
 * Member domain — registration, contact details, status tracking, and GDPR
 * erasure.
 *
 * <p>
 * Key components:
 * </p>
 * <ul>
 * <li>{@link at.mavila.dbchatbox.domain.club.member.Member} — club member
 * entity with identity and contact details</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.member.MemberStatusHistory} —
 * audit trail of status transitions</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.member.MemberService} — member
 * lifecycle management</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.member.MemberGdprService} — GDPR
 * Art. 17 anonymisation and purge</li>
 * </ul>
 *
 * @since 2026-04-09
 */
package at.mavila.dbchatbox.domain.club.member;
