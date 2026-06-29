package at.mavila.dbchatbox.domain.club.member;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.tenant.Tenant;
import at.mavila.dbchatbox.domain.club.tenant.TenantService;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import at.mavila.dbchatbox.domain.support.TsidGenerator;
import at.mavila.dbchatbox.infrastructure.security.KeycloakAdminClient;
import at.mavila.dbchatbox.infrastructure.security.KeycloakCreateUserRequest;
import at.mavila.dbchatbox.infrastructure.security.KeycloakUpdateUserRequest;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import lombok.RequiredArgsConstructor;

/**
 * Member service backed by Keycloak as the single source of truth for member identity.
 *
 * <p>
 * Reads ({@link #findAll}, {@link #findById}) come straight from the Keycloak Admin REST API for the
 * current tenant's realm. Writes ({@link #createMember}, {@link #updateMember},
 * {@link #anonymizeInKeycloak}) update Keycloak users; the only DB footprint is the lean
 * {@link Member} stub whose primary key equals the Keycloak {@code memberId} attribute, plus its
 * initial status-history row.
 * </p>
 *
 * <p>
 * The realm is always derived from {@link TenantContext} — never accepted from a caller. A
 * {@code null} tenant id propagates as {@link IllegalStateException}, matching the
 * {@code Auditable.prePersist()} contract.
 * </p>
 *
 * @since 2026-06-29
 */
@Component
@RequiredArgsConstructor
@Transactional
public class KeycloakMemberService {

  private static final String DELETED = "DELETED";
  private static final String DELETED_EMAIL_TEMPLATE = "deleted-%d@anonymous.local";

  private final MemberRepository memberRepository;
  private final MemberStatusHistoryRepository statusHistoryRepository;
  private final KeycloakAdminClient keycloakAdminClient;
  private final TenantService tenantService;
  private final CommandValidator commandValidator;

  /**
   * Lists members in the current tenant's realm, optionally filtered by current status.
   *
   * @param status the status to filter by, or {@code null} for all members
   * @return matching member views
   */
  @Transactional(readOnly = true)
  public List<MemberView> findAll(final Status status) {
    final List<MemberView> all = keycloakAdminClient.listMemberViews(currentRealm());
    if (isNull(status)) {
      return all;
    }
    return all.stream().filter(view -> getCurrentStatus(view) == status).toList();
  }

  /**
   * Finds a member by their TSID via the Keycloak {@code memberId} attribute.
   *
   * @param id the member TSID
   * @return the member view
   * @throws at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException if no member matches
   */
  @Transactional(readOnly = true)
  public MemberView findById(final Long id) {
    return keycloakAdminClient.findByMemberId(currentRealm(), id);
  }

  /**
   * Creates a member (idempotent). A TSID is generated and written as the Keycloak {@code memberId}
   * attribute; if Keycloak reports the email already exists (409), the existing user's identity is
   * reused instead. The lean DB stub is upserted and, when newly inserted, seeded with an initial
   * {@code ACTIVE} status entry.
   *
   * @param command the creation command
   * @return the created (or converged) member view
   */
  public MemberView createMember(final CreateMemberCommand command) {
    commandValidator.validate(command);
    final String realm = currentRealm();
    final long tsid = TsidGenerator.generate();
    final MemberView draft = new MemberView(tsid, null, command.firstName(), command.lastName(),
        command.email(), command.phoneNumber(), command.memberSince(), command.memberUntil(), null, null);

    final KeycloakCreateUserRequest req = new KeycloakCreateUserRequest(
        command.email(), command.firstName(), command.lastName(), command.email(), true, true, attributesOf(draft));

    final String keycloakSubject = keycloakAdminClient.createUser(realm, req);
    final MemberView view = keycloakAdminClient.getUserById(realm, keycloakSubject);
    upsertStub(view.id(), keycloakSubject);
    return view;
  }

  /**
   * Updates a member's personal data in Keycloak. Null command fields keep their current value; the
   * lean DB stub is not touched.
   *
   * @param id      the member TSID
   * @param command the update command (null fields are not changed)
   * @return the updated member view
   */
  public MemberView updateMember(final Long id, final UpdateMemberCommand command) {
    commandValidator.validate(command);
    final String realm = currentRealm();
    final MemberView current = keycloakAdminClient.findByMemberId(realm, id);
    final MemberView merged = merge(current, command);

    keycloakAdminClient.updateUser(realm, current.keycloakSubject(), new KeycloakUpdateUserRequest(
        merged.firstName(), merged.lastName(), merged.email(), true, attributesOf(merged)));

    return keycloakAdminClient.findByMemberId(realm, id);
  }

  /**
   * Anonymizes a member's Keycloak account as part of GDPR erasure: scrubs name and email, disables
   * the account, and clears the contact/membership-date attributes (keeping only {@code memberId}
   * so downstream FK references remain resolvable).
   *
   * @param id the member TSID
   */
  public void anonymizeInKeycloak(final Long id) {
    final String realm = currentRealm();
    final MemberView current = keycloakAdminClient.findByMemberId(realm, id);

    final Map<String, List<String>> attrs = new LinkedHashMap<>();
    attrs.put("memberId", List.of(String.valueOf(id)));
    attrs.put("updatedAt", List.of(OffsetDateTime.now().toString()));

    keycloakAdminClient.updateUser(realm, current.keycloakSubject(), new KeycloakUpdateUserRequest(
        DELETED, DELETED, DELETED_EMAIL_TEMPLATE.formatted(id), false, attrs));
  }

  /**
   * Returns the current status of a member from the latest status-history entry.
   *
   * @param view the member view
   * @return the current status, defaulting to {@code ACTIVE} when no history exists
   */
  @Transactional(readOnly = true)
  public Status getCurrentStatus(final MemberView view) {
    return statusHistoryRepository.findFirstByMemberIdOrderByChangedAtDesc(view.id())
        .map(MemberStatusHistory::getStatus).orElse(Status.ACTIVE);
  }

  // ---------------------------------------------------------------
  // internal helpers
  // ---------------------------------------------------------------

  private void upsertStub(final Long memberId, final String keycloakSubject) {
    if (memberRepository.existsById(memberId)) {
      return;
    }
    final Member stub = Member.builder().id(memberId).keycloakSubject(keycloakSubject).build();
    memberRepository.save(stub);
    statusHistoryRepository.save(MemberStatusHistory.builder()
        .member(stub).status(Status.ACTIVE).changedAt(LocalDateTime.now()).reason("Member registered").build());
  }

  private MemberView merge(final MemberView current, final UpdateMemberCommand command) {
    return new MemberView(
        current.id(),
        current.keycloakSubject(),
        coalesce(command.firstName(), current.firstName()),
        coalesce(command.lastName(), current.lastName()),
        coalesce(command.email(), current.email()),
        coalesce(command.phoneNumber(), current.phoneNumber()),
        nonNull(command.memberSince()) ? command.memberSince() : current.memberSince(),
        nonNull(command.memberUntil()) ? command.memberUntil() : current.memberUntil(),
        current.createdAt(),
        current.updatedAt());
  }

  private Map<String, List<String>> attributesOf(final MemberView view) {
    final Map<String, List<String>> attrs = new LinkedHashMap<>();
    attrs.put("memberId", List.of(String.valueOf(view.id())));
    if (nonNull(view.phoneNumber())) {
      attrs.put("phoneNumber", List.of(view.phoneNumber()));
    }
    if (nonNull(view.memberSince())) {
      attrs.put("memberSince", List.of(view.memberSince().toString()));
    }
    if (nonNull(view.memberUntil())) {
      attrs.put("memberUntil", List.of(view.memberUntil().toString()));
    }
    attrs.put("updatedAt", List.of(OffsetDateTime.now().toString()));
    return attrs;
  }

  private static String coalesce(final String candidate, final String fallback) {
    return nonNull(candidate) ? candidate : fallback;
  }

  private String currentRealm() {
    final Tenant tenant = tenantService.requireById(currentTenantOrThrow());
    return tenant.getKeycloakRealm();
  }

  private Long currentTenantOrThrow() {
    final Long tenantId = TenantContext.getTenantId();
    if (isNull(tenantId)) {
      throw new IllegalStateException("No tenant in context");
    }
    return tenantId;
  }
}
