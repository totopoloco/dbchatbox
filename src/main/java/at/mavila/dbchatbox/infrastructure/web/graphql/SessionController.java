package at.mavila.dbchatbox.infrastructure.web.graphql;

import static java.util.Objects.nonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.club.trainer.Trainer;
import at.mavila.dbchatbox.domain.club.training.Session;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrence;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceService;
import at.mavila.dbchatbox.domain.club.training.SessionOccurrenceStatus;
import at.mavila.dbchatbox.domain.club.training.SessionService;
import at.mavila.dbchatbox.domain.club.training.SessionType;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller for session and session occurrence queries and mutations.
 *
 * @since 2026-04-09
 */
@Controller
@RequiredArgsConstructor
public class SessionController {

  private final SessionService sessionService;
  private final SessionOccurrenceService occurrenceService;

  // ==================== QUERIES ====================

  @QueryMapping
  public List<Session> sessions(@Argument
  final String sessionType) {
    final SessionType type = nonNull(sessionType) ? SessionType.valueOf(sessionType) : null;
    return sessionService.findAll(type);
  }

  @QueryMapping
  public List<SessionOccurrence> sessionOccurrences(@Argument
  final Long sessionId, @Argument
  final LocalDate from, @Argument
  final LocalDate to, @Argument
  final String status) {
    final SessionOccurrenceStatus statusEnum = nonNull(status) ? SessionOccurrenceStatus.valueOf(status) : null;
    return occurrenceService.findOccurrences(sessionId, from, to, statusEnum);
  }

  @QueryMapping
  public List<SessionOccurrence> sessionsByMember(@Argument
  final Long memberId, @Argument
  final LocalDate from, @Argument
  final LocalDate to, @Argument
  final String sessionType) {
    return occurrenceService.findByMember(memberId, from, to);
  }

  @QueryMapping
  public SessionOccurrence nextSessionForMember(@Argument
  final Long memberId, @Argument
  final String sessionType) {
    return occurrenceService.findNextForMember(memberId);
  }

  @QueryMapping
  public List<Trainer> availableTrainers(@Argument
  final String dayOfWeek, @Argument
  final LocalTime startTime, @Argument
  final LocalTime endTime) {
    return sessionService.findAvailableTrainers(DayOfWeek.valueOf(dayOfWeek), startTime, endTime);
  }

  // ==================== MUTATIONS ====================

  @MutationMapping
  public Session createSession(@Argument
  final Map<String, Object> input) {
    return sessionService.createSession((String) input.get("name"),
        SessionType.valueOf((String) input.get("sessionType")), DayOfWeek.valueOf((String) input.get("dayOfWeek")),
        (LocalTime) input.get("startTime"), (LocalTime) input.get("endTime"), (String) input.get("location"),
        input.containsKey("trainerId") ? Long.valueOf(input.get("trainerId").toString()) : null);
  }

  @MutationMapping
  @SuppressWarnings("unchecked")
  public List<SessionOccurrence> createSessionOccurrences(@Argument
  final Map<String, Object> input) {
    @SuppressWarnings("unchecked")
    final List<LocalDate> skipDates = input.containsKey("skipDates") ? (List<LocalDate>) input.get("skipDates") : null;

    return occurrenceService.createOccurrences(Long.valueOf(input.get("sessionId").toString()),
        (LocalDate) input.get("startDate"), (LocalDate) input.get("endDate"), skipDates);
  }

  @MutationMapping
  public SessionOccurrence cancelSessionOccurrence(@Argument
  final Long id) {
    return occurrenceService.cancel(id);
  }

  @MutationMapping
  public SessionOccurrence completeSessionOccurrence(@Argument
  final Long id) {
    return occurrenceService.complete(id);
  }
}
