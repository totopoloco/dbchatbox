package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.math.BigDecimal;

import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;

/**
 * GraphQL output type for outstanding payment summaries.
 *
 * @since 2026-06-28
 */
public record MemberPaymentStatus(
    Member member,
    MemberSubscription subscription,
    MembershipType membershipType,
    BigDecimal amountDue,
    BigDecimal amountPaid,
    BigDecimal outstanding
) {}
