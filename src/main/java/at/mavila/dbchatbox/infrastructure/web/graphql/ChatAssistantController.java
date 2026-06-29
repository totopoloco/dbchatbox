package at.mavila.dbchatbox.infrastructure.web.graphql;

import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import at.mavila.dbchatbox.domain.chatbox.AskCommand;
import at.mavila.dbchatbox.domain.chatbox.AskResult;
import at.mavila.dbchatbox.domain.chatbox.ChatAssistantService;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL entry point for the chatbox.
 *
 * <p>
 * The controller owns no business logic — it converts the GraphQL input map to
 * an {@link AskCommand} and hands it to {@link ChatAssistantService#ask}. The
 * returned {@link AskResult} is a plain record whose fields line up with the
 * GraphQL {@code AskResult} type, so Spring for GraphQL serialises it
 * automatically.
 * </p>
 *
 * @since 2026-04-20
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatAssistantController {

  private final ChatAssistantService chatAssistantService;

  /**
   * Handles the {@code ask(input: AskInput!): AskResult!} query.
   *
   * <p>
   * Restricted to JWT-authenticated human roles (decision D-4): API-key callers carry only
   * {@code ROLE_M2M} and are rejected here, so any tool invoked downstream can rely on a JWT being
   * present (the member tools forward it to the Keycloak Admin API).
   * </p>
   *
   * @param input GraphQL input map: {@code { prompt: String!, locale: String }}
   * @return synthesised answer plus metadata
   */
  @QueryMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER') or hasRole('TRAINER')")
  public AskResult ask(@Argument("input") final Map<String, Object> input) {
    final String prompt = (String) input.get("prompt");
    final String locale = (String) input.get("locale");
    return chatAssistantService.ask(new AskCommand(prompt, locale));
  }
}
