package at.mavila.dbchatbox.domain.club.training;

import java.time.DayOfWeek;
import java.time.LocalTime;

import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.support.TsidGenerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Represents a recurring weekly schedule slot for a club activity (training,
 * free games, etc.).
 *
 * <p>
 * A session defines <em>when</em> and <em>where</em> an activity happens on a
 * weekly basis. Individual dated instances
 * are materialized as {@link SessionOccurrence} records.
 * </p>
 *
 * @since 2026-04-09
 */
@Entity
@Table(name = "session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

  @Id
  @TsidGenerated
  private Long id;

  @Column(nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "session_type", nullable = false, length = 50)
  private SessionType sessionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 10)
  private DayOfWeek dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(nullable = false, length = 200)
  private String location;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trainer_id")
  private Trainer trainer;

  @Version
  @Column(nullable = false)
  private Short version;
}
