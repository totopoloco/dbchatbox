package at.mavila.dbchatbox.domain.club.identity;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import at.mavila.dbchatbox.domain.club.member.MemberRepository;
import at.mavila.dbchatbox.domain.club.trainer.TrainerRepository;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import at.mavila.dbchatbox.infrastructure.security.TenantScopedFinder;

/**
 * Service for managing {@link AppUser} records — the link between Keycloak identities
 * and domain members/trainers.
 *
 * <p>Provisioning is lazy (JIT): the {@code AppUser} row is created on the first call to
 * {@link #currentUser()}, not on every authenticated request. This avoids a DB write on
 * every request for users who never call {@code me}.</p>
 *
 * @since 2026-06-28
 */
@Component
@RequiredArgsConstructor
@Transactional
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final MemberRepository memberRepository;
    private final TrainerRepository trainerRepository;
    private final TenantScopedFinder tenantScopedFinder;

    /**
     * Resolves the {@link AppUser} for the current JWT principal, JIT-provisioning it
     * if this is the first login (rule 84).
     *
     * @return the current user's AppUser record
     * @throws IllegalStateException if the security context holds no JWT
     */
    public AppUser currentUser() {
        final JwtAuthenticationToken jwt = currentJwt();
        final Long tenantId = TenantContext.getTenantId();
        final String subject = jwt.getToken().getSubject();

        return appUserRepository.findByTenantIdAndKeycloakSubject(tenantId, subject)
            .orElseGet(() -> provision(jwt, tenantId));
    }

    /**
     * Admin operation: links a Keycloak subject to a {@link at.mavila.dbchatbox.domain.club.member.Member}
     * or {@link at.mavila.dbchatbox.domain.club.trainer.Trainer} in the current tenant (rule 84).
     * At most one link may be active at a time.
     *
     * @param keycloakSubject the Keycloak {@code sub} claim of the user to link
     * @param memberId        the member to link to, or {@code null}
     * @param trainerId       the trainer to link to, or {@code null}
     * @return the updated AppUser
     */
    public AppUser link(final String keycloakSubject, final Long memberId, final Long trainerId) {
        if (nonNull(memberId) && nonNull(trainerId)) {
            throw new InvalidOperationException(
                "AppUser cannot be linked to both a member and a trainer simultaneously");
        }
        final Long tenantId = TenantContext.getTenantId();
        final AppUser user = appUserRepository.findByTenantIdAndKeycloakSubject(tenantId, keycloakSubject)
            .orElseThrow(() -> new ResourceNotFoundException("AppUser", keycloakSubject));

        if (nonNull(memberId)) {
            tenantScopedFinder.findById(memberRepository, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
            user.setMemberId(memberId);
            user.setTrainerId(null);
        } else if (nonNull(trainerId)) {
            tenantScopedFinder.findById(trainerRepository, trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer", trainerId));
            user.setTrainerId(trainerId);
            user.setMemberId(null);
        }

        return appUserRepository.save(user);
    }

    private AppUser provision(final JwtAuthenticationToken jwt, final Long tenantId) {
        final AppUser user = AppUser.builder()
            .keycloakSubject(jwt.getToken().getSubject())
            .username(jwt.getToken().getClaimAsString("preferred_username"))
            .email(jwt.getToken().getClaimAsString("email"))
            .active(true)
            .build();
        user.setTenantId(tenantId);
        return appUserRepository.save(user);
    }

    private static JwtAuthenticationToken currentJwt() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth;
        }
        throw new IllegalStateException("No JWT authentication in security context");
    }
}
