package at.mavila.dbchatbox.domain.support;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.time.OffsetDateTime;

import at.mavila.dbchatbox.infrastructure.security.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

/**
 * Abstract JPA base class that provides automatic audit timestamps and tenant
 * scoping
 * for all mutable domain entities.
 *
 * <p>
 * Subclasses inherit {@code createdAt}, {@code updatedAt}, and {@code tenantId}
 * columns,
 * which are populated by JPA lifecycle callbacks — no application code sets
 * them explicitly.
 * {@code createdAt} and {@code tenantId} are written once at insert and never
 * modified;
 * {@code updatedAt} is refreshed on every update.
 * </p>
 *
 * <p>
 * Annotated with {@link MappedSuperclass} so JPA maps the inherited columns
 * into each owning entity's table — no separate {@code auditable} table is
 * created.
 * </p>
 *
 * @since 2026-05-01
 */
@MappedSuperclass
@Getter
public abstract class Auditable {

  /**
   * The timestamp at which this entity was first persisted. Set once by
   * {@link #prePersist()} and never modified afterwards — the
   * {@code updatable = false}
   * column constraint prevents accidental overwrites by JPA.
   *
   * <p>
   * Exposed in the GraphQL API as {@code createdAt: DateTime!}.
   * </p>
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /**
   * The timestamp of the most recent write to this entity. Initialized alongside
   * {@link #createdAt} in {@link #prePersist()} and refreshed on every subsequent
   * update by {@link #preUpdate()}.
   *
   * <p>
   * Internal infrastructure field — not exposed in the GraphQL schema.
   * </p>
   */
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /**
   * The tenant that owns this entity. Set once at insert from
   * {@link TenantContext}
   * and never changed — the {@code updatable = false} constraint enforces this.
   *
   * <p>
   * Never accept this value from the client; it is always derived server-side
   * from the authenticated request context (spec rules 76, 82).
   * </p>
   */
  @Setter
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private Long tenantId;

  /**
   * JPA lifecycle callback invoked before a new entity is inserted. Initializes
   * {@link #createdAt}, {@link #updatedAt}, and {@link #tenantId} from
   * {@link TenantContext}. Fails immediately if no tenant is in context — a
   * tenant-owned row must never be written without a tenant (spec rules 76, 82).
   *
   * @throws IllegalStateException if {@code TenantContext} holds no tenant ID
   */
  @PrePersist
  void prePersist() {
    this.createdAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();

    if (isNull(this.tenantId)) {
      this.tenantId = TenantContext.getTenantId();
    }

    if (nonNull(this.tenantId)) {
      return;
    }

    throw new IllegalStateException(
        "Cannot persist a tenant-owned entity without a tenant in context");
  }

  /**
   * JPA lifecycle callback invoked before an existing entity is updated.
   * Refreshes {@link #updatedAt} to the current timestamp; {@link #createdAt}
   * and {@link #tenantId} are intentionally left unchanged.
   */
  @PreUpdate
  void preUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
