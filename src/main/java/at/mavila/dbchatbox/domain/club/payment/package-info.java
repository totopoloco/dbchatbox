/**
 * Payment domain — recording payments, payment-document upload, and admin
 * verification workflow.
 *
 * <p>
 * Key components:
 * </p>
 * <ul>
 * <li>{@link at.mavila.dbchatbox.domain.club.payment.Payment} — payment against
 * a subscription</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.payment.PaymentDocument} — payment
 * proof document (bank-issued PDF)</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.payment.PaymentService} — payment
 * recording and outstanding-dues queries</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.payment.PaymentDocumentService} —
 * document upload/review workflow</li>
 * </ul>
 *
 * @since 2026-04-09
 */
package at.mavila.dbchatbox.domain.club.payment;
