package at.mavila.dbchatbox.infrastructure.security;

import static java.util.Objects.isNull;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.support.Auditable;

/**
 * Centralises the "load by id within the current tenant" pattern so no service can
 * accidentally return another tenant's row from a guessed id (spec rule 77).
 *
 * <p>A {@code null} {@link TenantContext} always returns empty — fail closed (rule 82).
 * This is the single most important invariant: null tenant means no data, never all tenants.</p>
 *
 * @since 2026-06-28
 */
@Component
public class TenantScopedFinder {

    /**
     * Loads the entity by id and returns it only if it belongs to the current tenant;
     * otherwise returns empty.
     *
     * @param repo the repository to load from
     * @param id   the entity primary key
     * @param <T>  the entity type — must extend {@link Auditable} so tenant membership is checkable
     * @return the entity if found in the current tenant's scope, otherwise empty
     */
    public <T extends Auditable> Optional<T> findById(final JpaRepository<T, Long> repo, final Long id) {
        final Long tenantId = TenantContext.getTenantId();
        if (isNull(tenantId)) {
            return Optional.empty();
        }
        return repo.findById(id).filter(entity -> tenantId.equals(entity.getTenantId()));
    }
}
