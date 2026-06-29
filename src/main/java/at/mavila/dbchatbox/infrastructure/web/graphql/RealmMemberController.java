package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.tenant.Tenant;
import at.mavila.dbchatbox.domain.club.tenant.TenantService;
import at.mavila.dbchatbox.infrastructure.security.KeycloakAdminClient;
import at.mavila.dbchatbox.infrastructure.security.TenantContext;

/**
 * GraphQL controller for Keycloak realm user queries.
 *
 * <p>{@code realmMembers} is restricted to ADMIN users because it proxies a call
 * to the Keycloak Admin REST API, which returns PII for all members of the tenant.</p>
 *
 * @since 2026-06-28
 */
@Controller
@RequiredArgsConstructor
public class RealmMemberController {

    private final TenantService tenantService;
    private final KeycloakAdminClient keycloakAdminClient;

    /**
     * Lists all Keycloak users with the {@code MEMBER} role in the current tenant's realm.
     *
     * <p>The caller's JWT is forwarded directly to the Keycloak Admin REST API.
     * In addition to the application {@code ADMIN} role, the caller's Keycloak account
     * must hold the {@code realm-management/view-users} client role — add it to the
     * {@code ADMIN} users in the realm import JSON.</p>
     *
     * @return realm users with the MEMBER role
     */
    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RealmUser> realmMembers() {
        final Tenant tenant = tenantService.requireById(TenantContext.getTenantId());
        return keycloakAdminClient.getMembersInRealm(tenant.getKeycloakRealm());
    }
}
