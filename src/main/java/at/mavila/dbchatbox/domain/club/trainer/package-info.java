/**
 * Trainer domain — trainer identity, compensation settings, hour tracking, and
 * approval workflow.
 *
 * <p>
 * Key components:
 * </p>
 * <ul>
 * <li>{@link at.mavila.dbchatbox.domain.club.trainer.Trainer} — trainer
 * identity and contact details</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.trainer.TrainerSettings} —
 * per-trainer compensation and workflow configuration</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.trainer.TrainerLog} — trainer hour
 * submissions with approval workflow</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.trainer.TrainerService} — trainer
 * registration and settings management</li>
 * <li>{@link at.mavila.dbchatbox.domain.club.trainer.TrainerLogService} — hour
 * submission, approval, and payment summaries</li>
 * </ul>
 *
 * @since 2026-04-09
 */
package at.mavila.dbchatbox.domain.club.trainer;
