package at.mavila.dbchatbox.domain.club.trainer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TrainerSettings} entities.
 *
 * @since 2026-04-10
 */
@Repository
public interface TrainerSettingsRepository extends JpaRepository<TrainerSettings, Long> {

  Optional<TrainerSettings> findByTrainerId(Long trainerId);
}
