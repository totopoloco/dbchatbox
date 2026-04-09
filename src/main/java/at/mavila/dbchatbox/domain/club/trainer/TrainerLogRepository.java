package at.mavila.dbchatbox.domain.club.trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TrainerLog} entities.
 *
 * @since 2026-04-09
 */
@Repository
public interface TrainerLogRepository extends JpaRepository<TrainerLog, Long> {

  /**
   * Checks if a log entry exists for a given trainer and session occurrence.
   *
   * @param trainerId
   *                              the trainer ID
   * @param sessionOccurrenceId
   *                              the session occurrence ID
   * @return {@code true} if a log entry exists
   */
  boolean existsByTrainerIdAndSessionOccurrenceId(Long trainerId, Long sessionOccurrenceId);

  /**
   * Finds all log entries for a trainer with a given status.
   *
   * @param trainerId
   *                    the trainer ID
   * @param status
   *                    the log status
   * @return matching log entries
   */
  List<TrainerLog> findByTrainerIdAndStatus(Long trainerId, TrainerLogStatus status);

  /**
   * Finds all log entries with a given status.
   *
   * @param status
   *                 the log status
   * @return matching log entries
   */
  List<TrainerLog> findByStatus(TrainerLogStatus status);

  /**
   * Finds approved log entries for a trainer within a date range.
   *
   * @param trainerId
   *                    the trainer ID
   * @param from
   *                    start date
   * @param to
   *                    end date
   * @return approved log entries in the range
   */
  @Query("SELECT tl FROM TrainerLog tl WHERE tl.trainer.id = :trainerId " + "AND tl.status = 'APPROVED' "
      + "AND tl.sessionOccurrence.date BETWEEN :from AND :to")
  List<TrainerLog> findApprovedByTrainerAndDateRange(@Param("trainerId")
  Long trainerId, @Param("from")
  LocalDate from, @Param("to")
  LocalDate to);

  /**
   * Calculates total approved hours for a trainer in a date range.
   *
   * @param trainerId
   *                    the trainer ID
   * @param from
   *                    start date
   * @param to
   *                    end date
   * @return total hours, or 0 if none
   */
  @Query("SELECT COALESCE(SUM(tl.hoursWorked), 0) FROM TrainerLog tl "
      + "WHERE tl.trainer.id = :trainerId AND tl.status = 'APPROVED' "
      + "AND tl.sessionOccurrence.date BETWEEN :from AND :to")
  BigDecimal sumApprovedHoursByTrainerAndDateRange(@Param("trainerId")
  Long trainerId, @Param("from")
  LocalDate from, @Param("to")
  LocalDate to);

  /**
   * Counts approved log entries for a trainer in a date range.
   *
   * @param trainerId
   *                    the trainer ID
   * @param from
   *                    start date
   * @param to
   *                    end date
   * @return count of approved entries
   */
  @Query("SELECT COUNT(tl) FROM TrainerLog tl " + "WHERE tl.trainer.id = :trainerId AND tl.status = 'APPROVED' "
      + "AND tl.sessionOccurrence.date BETWEEN :from AND :to")
  long countApprovedByTrainerAndDateRange(@Param("trainerId")
  Long trainerId, @Param("from")
  LocalDate from, @Param("to")
  LocalDate to);

  /**
   * Calculates total pending hours for a trainer in a date range.
   *
   * @param trainerId
   *                    the trainer ID
   * @param from
   *                    start date
   * @param to
   *                    end date
   * @return total pending hours
   */
  @Query("SELECT COALESCE(SUM(tl.hoursWorked), 0) FROM TrainerLog tl "
      + "WHERE tl.trainer.id = :trainerId AND tl.status = 'PENDING' "
      + "AND tl.sessionOccurrence.date BETWEEN :from AND :to")
  BigDecimal sumPendingHoursByTrainerAndDateRange(@Param("trainerId")
  Long trainerId, @Param("from")
  LocalDate from, @Param("to")
  LocalDate to);

  /**
   * Counts pending log entries for a trainer in a date range.
   *
   * @param trainerId
   *                    the trainer ID
   * @param from
   *                    start date
   * @param to
   *                    end date
   * @return count of pending entries
   */
  @Query("SELECT COUNT(tl) FROM TrainerLog tl " + "WHERE tl.trainer.id = :trainerId AND tl.status = 'PENDING' "
      + "AND tl.sessionOccurrence.date BETWEEN :from AND :to")
  long countPendingByTrainerAndDateRange(@Param("trainerId")
  Long trainerId, @Param("from")
  LocalDate from, @Param("to")
  LocalDate to);
}
