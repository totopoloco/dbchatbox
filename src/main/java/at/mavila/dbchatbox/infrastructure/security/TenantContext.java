package at.mavila.dbchatbox.infrastructure.security;

/**
 * Thread-local holder for the current request's tenant ID.
 *
 * <p>Populated by the authentication filter on every authenticated request and cleared in
 * {@code finally} at the end of the request. Fail closed: a {@code null} value must never
 * be treated as "all tenants" — callers must reject when null (rule 82).</p>
 *
 * @since 2026-06-28
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    /**
     * Sets the current tenant ID for this request thread.
     *
     * @param tenantId the tenant's primary key
     */
    public static void setTenantId(final Long tenantId) {
        CURRENT.set(tenantId);
    }

    /**
     * Returns the current tenant ID, or {@code null} if not set.
     * Callers must treat {@code null} as "no access" — never as "all tenants".
     *
     * @return the current tenant ID, possibly null
     */
    public static Long getTenantId() {
        return CURRENT.get();
    }

    /**
     * Clears the current tenant context. Always call in a {@code finally} block to
     * prevent tenant leakage across pooled threads.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
