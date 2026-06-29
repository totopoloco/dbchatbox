package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.time.LocalDate;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;

/**
 * GraphQL output type for overdue subscription summaries.
 *
 * @since 2026-06-28
 */
public record OverdueSubscription(
    Member member,
    MemberSubscription subscription,
    MembershipType membershipType,
    String paymentStatus,
    LocalDate dueDate,
    int daysOverdue
) {}
