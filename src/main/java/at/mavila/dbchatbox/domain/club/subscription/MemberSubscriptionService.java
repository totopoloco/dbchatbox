package at.mavila.dbchatbox.domain.club.subscription;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.MemberDeletedException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.member.Member;
import at.mavila.dbchatbox.domain.club.member.MemberRepository;
import at.mavila.dbchatbox.domain.club.member.MemberService;
import at.mavila.dbchatbox.domain.club.member.Status;
import at.mavila.dbchatbox.domain.club.membership.MembershipType;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeRepository;
import at.mavila.dbchatbox.domain.club.membership.MembershipTypeStatus;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for member subscriptions — subscribe, renew, end, and proration.
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MemberSubscriptionService {

  private final MemberSubscriptionRepository subscriptionRepository;
  private final MemberRepository memberRepository;
  private final MembershipTypeRepository membershipTypeRepository;
  private final MemberService memberService;

  /**
   * Subscribes a member to a membership type.
   *
   * @param memberId
   *                           the member ID
   * @param membershipTypeId
   *                           the membership type ID
   * @param startDate
   *                           the start date
   * @param endDate
   *                           the end date (null to compute from duration)
   * @param agreedPrice
   *                           the agreed price (null to compute from type or prorate)
   * @return the created subscription
   * @throws MemberNotFoundException
   *                                     if the member does not exist
   * @throws MemberDeletedException
   *                                     if the member is DELETED
   * @throws ResourceNotFoundException
   *                                     if the membership type does not exist
   * @throws InvalidOperationException
   *                                     if the membership type is not ACTIVE
   */
  public MemberSubscription subscribeMember(final Long memberId, final Long membershipTypeId, final LocalDate startDate,
      final LocalDate endDate, final BigDecimal agreedPrice) {
    final Member member = memberRepository.findById(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));

    if (memberService.getCurrentStatus(member) == Status.DELETED) {
      throw new MemberDeletedException(memberId);
    }

    final MembershipType type = membershipTypeRepository.findById(membershipTypeId)
        .orElseThrow(() -> new ResourceNotFoundException("MembershipType", membershipTypeId));

    if (type.getStatus() != MembershipTypeStatus.ACTIVE) {
      throw new InvalidOperationException(
          "Can only subscribe to ACTIVE membership types, current status: %s".formatted(type.getStatus().name()));
    }

    final LocalDate computedEndDate = nonNull(endDate) ? endDate : computeEndDate(startDate, type);
    final BigDecimal computedPrice = resolveAgreedPrice(agreedPrice, type, startDate, computedEndDate);

    final MemberSubscription subscription = MemberSubscription.builder().member(member).membershipType(type)
        .startDate(startDate).endDate(computedEndDate).agreedPrice(computedPrice).build();

    return subscriptionRepository.save(subscription);
  }

  /**
   * Ends a subscription early by setting endDate to today.
   *
   * @param id
   *             the subscription ID
   * @return the updated subscription
   * @throws ResourceNotFoundException
   *                                     if the subscription does not exist
   */
  public MemberSubscription endSubscription(final Long id) {
    final MemberSubscription subscription = subscriptionRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MemberSubscription", id));

    final LocalDate today = LocalDate.now();
    if (subscription.getEndDate().isAfter(today)) {
      subscription.setEndDate(today);
      return subscriptionRepository.save(subscription);
    }

    return subscription;
  }

  /**
   * Lists subscriptions for a member, optionally filtering by active state.
   *
   * @param memberId
   *                     the member ID
   * @param activeOnly
   *                     true to return only active subscriptions (endDate >= today)
   * @return matching subscriptions
   */
  @Transactional(readOnly = true)
  public List<MemberSubscription> findByMember(final Long memberId, final Boolean activeOnly) {
    if (Boolean.TRUE.equals(activeOnly)) {
      return subscriptionRepository.findByMemberIdAndEndDateGreaterThanEqual(memberId, LocalDate.now());
    }
    return subscriptionRepository.findByMemberId(memberId);
  }

  private LocalDate computeEndDate(final LocalDate startDate, final MembershipType type) {
    return switch (type.getUnit()) {
    case DAYS -> startDate.plusDays(type.getDuration());
    case WEEKS -> startDate.plusWeeks(type.getDuration());
    case MONTHS -> startDate.plusMonths(type.getDuration());
    case YEARS -> startDate.plusYears(type.getDuration());
    };
  }

  private BigDecimal resolveAgreedPrice(final BigDecimal explicitPrice, final MembershipType type,
      final LocalDate startDate, final LocalDate endDate) {
    // Manual override always wins
    if (nonNull(explicitPrice)) {
      return explicitPrice;
    }

    // Automatic proration
    if (Boolean.TRUE.equals(type.getProratedMode())) {
      return computeProratedPrice(type, startDate, endDate);
    }

    // Default: use the membership type's current price
    return type.getPrice();
  }

  private BigDecimal computeProratedPrice(final MembershipType type, final LocalDate startDate,
      final LocalDate endDate) {
    final LocalDate fullPeriodEnd = computeEndDate(startDate, type);
    final long totalPeriodDays = ChronoUnit.DAYS.between(startDate, fullPeriodEnd);

    if (totalPeriodDays <= 0) {
      return type.getPrice();
    }

    final long remainingDays = ChronoUnit.DAYS.between(startDate, endDate);
    return type.getPrice().multiply(BigDecimal.valueOf(remainingDays)).divide(BigDecimal.valueOf(totalPeriodDays), 2,
        RoundingMode.HALF_UP);
  }
}
