package at.mavila.dbchatbox;

import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

/**
 * Abstract base class for all {@link SpringBootTest} tests that persist or read
 * tenant-owned entities.
 *
 * <p>Adding {@code tenant_id} to {@link at.mavila.dbchatbox.domain.support.Auditable}'s
 * {@code @PrePersist} means every JPA INSERT must have a tenant in context. This class
 * populates {@link TenantContext} before each test with the WAT Simmering fixture tenant
 * (id = 1, seeded by {@code V7__add_tenant_and_identity.sql}) and clears it afterwards.</p>
 *
 * <p>GraphQL integration tests that also need a security context should extend this class
 * and additionally annotate with
 * {@code @WithMockUser(roles = "ADMIN")} (or the appropriate role) at the class or method
 * level. {@code @WithMockUser} creates a {@code UsernamePasswordAuthenticationToken} — not a
 * JWT — so {@code TenantResolutionFilter} does not fire; the tenant comes from this base
 * class's {@code @BeforeEach}.</p>
 *
 * @since 2026-06-28
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
public abstract class TenantAwareIntegrationTest {

    /** ID of the WAT Simmering fixture tenant seeded by V7. */
    protected static final Long TEST_TENANT_ID = 1L;

    /**
     * Sets the test tenant in {@link TenantContext} before each test so that
     * {@code Auditable.@PrePersist} can write the required {@code tenant_id}.
     */
    @BeforeEach
    void setUpTenantContext() {
        TenantContext.setTenantId(TEST_TENANT_ID);
    }

    /**
     * Clears the tenant context after each test to prevent leakage between tests.
     */
    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }
}
