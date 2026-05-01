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
 * Subclasses inherit {@code createdAt} and {@code updatedAt} columns, which are
 * populated by JPA lifecycle callbacks — no application code sets them explicitly.
 * {@code createdAt} is written once at insert and never modified; {@code updatedAt}
 * is refreshed on every update.
 * </p>
 *
 * <p>
 * Annotated with {@link MappedSuperclass} so JPA maps the inherited columns into
 * each owning entity's table — no separate {@code auditable} table is created.
 * </p>
 *
 * @since 2026-05-01
 */
@MappedSuperclass
@Getter
public abstract class Auditable {

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
