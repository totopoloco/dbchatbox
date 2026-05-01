package at.mavila.dbchatbox.domain.support;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

/**
 * Abstract JPA base class that provides automatic audit timestamps for all mutable domain entities.
 *
 * <p>
 * Subclasses inherit {@code createdAt} and {@code updatedAt} columns, which are populated by JPA lifecycle callbacks —
 * no application code sets them explicitly. {@code createdAt} is written once at insert and never modified;
 * {@code updatedAt} is refreshed on every update.
 * </p>
 *
 * <p>
 * Annotated with {@link MappedSuperclass} so JPA maps the inherited columns into each owning entity's table — no
 * separate {@code auditable} table is created.
 * </p>
 *
 * @since 2026-05-01
 */
@MappedSuperclass
@Getter
public abstract class Auditable {

  /**
   * The timestamp at which this entity was first persisted. Set once by {@link #prePersist()} and never modified
   * afterwards — the {@code updatable = false} column constraint prevents accidental overwrites by JPA.
   *
   * <p>
   * Exposed in the GraphQL API as {@code createdAt: DateTime!}.
   * </p>
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * The timestamp of the most recent write to this entity. Initialized alongside {@link #createdAt} in
   * {@link #prePersist()} and refreshed on every subsequent update by {@link #preUpdate()}.
   *
   * <p>
   * Internal infrastructure field — not exposed in the GraphQL schema.
   * </p>
   */
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * JPA lifecycle callback invoked before a new entity is inserted. Initializes both {@link #createdAt} and
   * {@link #updatedAt} to the current timestamp.
   */
  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  /**
   * JPA lifecycle callback invoked before an existing entity is updated. Refreshes {@link #updatedAt} to the current
   * timestamp; {@link #createdAt} is intentionally left unchanged.
   */
  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
