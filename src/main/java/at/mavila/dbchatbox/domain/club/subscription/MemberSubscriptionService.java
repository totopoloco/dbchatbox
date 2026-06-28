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
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for member subscriptions — subscribe, renew, end, and
 * proration.
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
  private final CommandValidator commandValidator;
  private final MemberService memberService;
  private final TenantScopedFinder tenantScopedFinder;

  /**
   * Subscribes a member to a membership type.
   *
   * @param command
   *                the subscribe command
   * @return the created subscription
   * @throws MemberNotFoundException
   *                                   if the member does not exist
   * @throws MemberDeletedException
   *                                   if the member is DELETED
   * @throws ResourceNotFoundException
   *                                   if the membership type does not exist
   * @throws InvalidOperationException
   *                                   if the membership type is not ACTIVE
   */
  public MemberSubscription subscribeMember(final SubscribeMemberCommand command) {
    commandValidator.validate(command);

    final Member member = tenantScopedFinder.findById(memberRepository, command.memberId())
        .orElseThrow(() -> new MemberNotFoundException(command.memberId()));

    if (memberService.getCurrentStatus(member) == Status.DELETED) {
      throw new MemberDeletedException(command.memberId());
    }

    final MembershipType type = tenantScopedFinder.findById(membershipTypeRepository, command.membershipTypeId())
        .orElseThrow(() -> new ResourceNotFoundException("MembershipType", command.membershipTypeId()));

    if (type.getStatus() != MembershipTypeStatus.ACTIVE) {
      throw new InvalidOperationException(
          "Can only subscribe to ACTIVE membership types, current status: %s".formatted(type.getStatus().name()));
    }

    final LocalDate computedEndDate = nonNull(command.endDate()) ? command.endDate()
        : computeEndDate(command.startDate(), type);
    final BigDecimal computedPrice = resolveAgreedPrice(command.agreedPrice(), type, command.startDate(),
        computedEndDate);

    final MemberSubscription subscription = MemberSubscription.builder().member(member).membershipType(type)
        .startDate(command.startDate()).endDate(computedEndDate).agreedPrice(computedPrice)
        .paymentStatus(SubscriptionPaymentStatus.NOT_PAID).build();

    return subscriptionRepository.save(subscription);
  }

  /**
   * Ends a subscription early by setting endDate to today.
   *
   * @param id
   *           the subscription ID
   * @return the updated subscription
   * @throws ResourceNotFoundException
   *                                   if the subscription does not exist
   */
  public MemberSubscription endSubscription(final Long id) {
    final MemberSubscription subscription = tenantScopedFinder.findById(subscriptionRepository, id)
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
   *                   the member ID
   * @param activeOnly
   *                   true to return only active subscriptions (endDate >= today)
   * @return matching subscriptions
   */
  @Transactional(readOnly = true)
  public List<MemberSubscription> findByMember(final Long memberId, final Boolean activeOnly) {
    tenantScopedFinder.findById(memberRepository, memberId)
        .orElseThrow(() -> new MemberNotFoundException(memberId));

    if (Boolean.TRUE.equals(activeOnly)) {
      return subscriptionRepository.findByMemberIdAndEndDateGreaterThanEqual(memberId, LocalDate.now());
    }
    return subscriptionRepository.findByMemberId(memberId);
  }

  /**
   * Finds subscriptions past their grace period with payment status not REVIEWED.
   *
   * @return overdue subscriptions
   */
  @Transactional(readOnly = true)
  public List<MemberSubscription> findOverdueSubscriptions() {
    final LocalDate today = LocalDate.now();
    return subscriptionRepository.findOverdueCandidates(today, SubscriptionPaymentStatus.REVIEWED)
        .stream()
        .filter(sub -> sub.getStartDate().plusDays(sub.getMembershipType().getGracePeriodDays()).isBefore(today))
        .toList();
  }

  /**
   * Finds subscriptions with payment status IN_REVIEW (awaiting admin
   * verification).
   *
   * @return subscriptions pending payment review
   */
  @Transactional(readOnly = true)
  public List<MemberSubscription> findPendingPaymentReviews() {
    return subscriptionRepository.findByPaymentStatus(SubscriptionPaymentStatus.IN_REVIEW);
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
    if (nonNull(explicitPrice)) {
      return explicitPrice;
    }

    if (Boolean.TRUE.equals(type.getProratedMode())) {
      return computeProratedPrice(type, startDate, endDate);
    }

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
