package at.mavila.dbchatbox.domain.club.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AppUser} entities.
 *
 * @since 2026-06-28
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Finds the app user for a given tenant and Keycloak subject — used for JIT provisioning
     * on first login.
     *
     * @param tenantId        the tenant's primary key
     * @param keycloakSubject the Keycloak {@code sub} claim
     * @return the app user if already provisioned
     */
    Optional<AppUser> findByTenantIdAndKeycloakSubject(Long tenantId, String keycloakSubject);
}
