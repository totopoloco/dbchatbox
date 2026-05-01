package at.mavila.dbchatbox.domain.club.payment;

import java.time.LocalDateTime;

import at.mavila.dbchatbox.domain.club.subscription.MemberSubscription;
import at.mavila.dbchatbox.domain.support.Auditable;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores a payment proof document (bank-issued PDF) uploaded by a member for a specific subscription.
 *
 * <p>
 * The document serves as evidence of payment and triggers the admin verification workflow. Only PDF files are accepted
 * ({@code contentType} must be {@code application/pdf}). The actual file is stored outside the database; the
 * {@code storagePath} field holds the reference.
 * </p>
 *
 * <p>
 * Inherits {@code createdAt} and {@code updatedAt} audit timestamps from
 * {@link at.mavila.dbchatbox.domain.support.Auditable}.
 * </p>
 *
 * @since 2026-04-13
 */
@Entity
@Table(name = "payment_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDocument extends Auditable {

  @Id
  @TsidGenerated
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_subscription_id", nullable = false)
  private MemberSubscription memberSubscription;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "storage_path", nullable = false, length = 1000)
  private String storagePath;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "uploaded_at", nullable = false)
  private LocalDateTime uploadedAt;

  @Column(length = 500)
  private String notes;

  @Version
  @Column(nullable = false)
  private Short version;
}
