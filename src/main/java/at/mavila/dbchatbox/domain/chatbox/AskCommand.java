package at.mavila.dbchatbox.domain.chatbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Validated input command for the {@code ask} GraphQL query.
 *
 * <p>
 * The {@link at.mavila.dbchatbox.domain.support.CommandValidator} is invoked in
 * {@link ChatAssistantService#ask(AskCommand)} — same pattern as every other
 * domain service in the project. Jakarta Bean Validation annotations are the
 * single source of truth for constraints; no manual guards in the service.
 * </p>
 *
 * @param prompt the user's natural-language question (required, 1–2000 characters)
 * @param locale optional BCP-47 locale tag (e.g. {@code en-US}, {@code de-AT}). If
 *               null, the LLM infers the language from the prompt itself.
 * @since 2026-04-20
 */
public record AskCommand(

    @NotBlank(message = "Prompt must not be blank")
    @Size(min = 1, max = 2000, message = "Prompt must be between 1 and 2000 characters")
    String prompt,

    @Size(max = 20, message = "Locale tag must not exceed 20 characters")
    String locale) {
}
