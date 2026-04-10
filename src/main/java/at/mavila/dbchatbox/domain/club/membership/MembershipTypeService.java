package at.mavila.dbchatbox.domain.club.membership;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.DuplicateNameException;
import at.mavila.dbchatbox.domain.club.exception.InvalidStatusTransitionException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.training.Session;
import at.mavila.dbchatbox.domain.club.training.SessionRepository;
import lombok.RequiredArgsConstructor;

/**
 * Domain service for membership type management — creation, status transitions, session linkage.
 *
 * @since 2026-04-09
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MembershipTypeService {

  private static final Set<String> VALID_TRANSITIONS = Set.of("DRAFT->ACTIVE", "ACTIVE->INACTIVE", "INACTIVE->ACTIVE",
      "DRAFT->INACTIVE");

  private final MembershipTypeRepository membershipTypeRepository;
  private final SessionRepository sessionRepository;

  /**
   * Creates a new membership type in DRAFT status.
   *
   * @param command
   *                  the creation command
   * @return the created membership type
   * @throws DuplicateNameException
   *                                  if the name is already in use
   */
  public MembershipType create(final CreateMembershipTypeCommand command) {
    if (membershipTypeRepository.existsByName(command.name())) {
      throw new DuplicateNameException("MembershipType", command.name());
    }

    final MembershipType type = MembershipType.builder().name(command.name()).description(command.description())
        .price(command.price()).duration(command.duration()).unit(command.unit()).status(MembershipTypeStatus.DRAFT)
        .proratedMode(isNull(command.proratedMode()) ? false : command.proratedMode()).build();

    return membershipTypeRepository.save(type);
  }

  /**
   * Transitions the membership type to a new status.
   *
   * @param id
   *                    the membership type ID
   * @param newStatus
   *                    the target status
   * @return the updated membership type
   * @throws ResourceNotFoundException
   *                                            if the membership type does not exist
   * @throws InvalidStatusTransitionException
   *                                            if the transition is not allowed
   */
  public MembershipType changeStatus(final Long id, final MembershipTypeStatus newStatus) {
    final MembershipType type = findByIdOrThrow(id);
    final String transitionKey = "%s->%s".formatted(type.getStatus().name(), newStatus.name());

    if (!VALID_TRANSITIONS.contains(transitionKey)) {
      throw new InvalidStatusTransitionException(type.getStatus().name(), newStatus.name());
    }

    type.setStatus(newStatus);
    return membershipTypeRepository.save(type);
  }

  /**
   * Links a session to a membership type.
   *
   * @param membershipTypeId
   *                           the membership type ID
   * @param sessionId
   *                           the session ID
   * @return the updated membership type
   */
  public MembershipType assignSession(final Long membershipTypeId, final Long sessionId) {
    final MembershipType type = findByIdOrThrow(membershipTypeId);
    final Session session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

    type.getSessions().add(session);
    return membershipTypeRepository.save(type);
  }

  /**
   * Unlinks a session from a membership type.
   *
   * @param membershipTypeId
   *                           the membership type ID
   * @param sessionId
   *                           the session ID
   * @return the updated membership type
   */
  public MembershipType removeSession(final Long membershipTypeId, final Long sessionId) {
    final MembershipType type = findByIdOrThrow(membershipTypeId);
    type.getSessions().removeIf(s -> s.getId().equals(sessionId));
    return membershipTypeRepository.save(type);
  }

  /**
   * Lists all membership types, optionally filtered by status.
   *
   * @param status
   *                 the status filter (null for all)
   * @return matching membership types
   */
  @Transactional(readOnly = true)
  public List<MembershipType> findAll(final MembershipTypeStatus status) {
    if (nonNull(status)) {
      return membershipTypeRepository.findByStatus(status);
    }
    return membershipTypeRepository.findAll();
  }

  /**
   * Finds a membership type by ID.
   *
   * @param id
   *             the ID
   * @return the membership type
   * @throws ResourceNotFoundException
   *                                     if not found
   */
  @Transactional(readOnly = true)
  public MembershipType findById(final Long id) {
    return findByIdOrThrow(id);
  }

  private MembershipType findByIdOrThrow(final Long id) {
    return membershipTypeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MembershipType", id));
  }
}
