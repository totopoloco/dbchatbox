package at.mavila.dbchatbox.domain.chatbox;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import at.mavila.dbchatbox.domain.chatbox.exception.ChatProviderUnavailableException;
import at.mavila.dbchatbox.domain.support.CommandValidator;
import lombok.RequiredArgsConstructor;

/**
 * Orchestrates a single {@code ask} invocation end-to-end.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Validate the {@link AskCommand} via {@link CommandValidator} (Jakarta
 *       Bean Validation on the record — same pattern as every other domain
 *       service in the project).</li>
 *   <li>Check the global per-hour rate limit (Phase 1 — no per-user limit
 *       exists until authentication lands).</li>
 *   <li>Hand the prompt to Spring AI's {@link ChatClient}. The client was
 *       configured at startup (see {@code ChatClientConfiguration}) with the
 *       system prompt and the full tool catalogue — every
 *       {@code @Tool}-annotated method in {@code domain.chatbox.tools}. Spring
 *       AI runs the full tool-calling loop internally: if the LLM requests a
 *       tool, Spring AI invokes the matching Java method (through Spring
 *       proxying, so {@code @Transactional} still applies), feeds the result
 *       back, and continues until the model emits its final text.</li>
 *   <li>Assemble an {@link AskResult} with the final text, the model id,
 *       provider-reported token usage, and end-to-end latency.</li>
 * </ol>
 *
 * <h2>Why {@code @Transactional(readOnly = true)}?</h2>
 *
 * <p>
 * The ChatClient call is not itself transactional, but every tool it may
 * invoke delegates to a {@code @Transactional(readOnly = true)} domain
 * service. Running the whole {@code ask} under a read-only transaction
 * guarantees a consistent read snapshot across tool calls and prevents
 * accidental writes from a mis-configured tool.
 * </p>
 *
 * @since 2026-04-20
 */
@Service
@RequiredArgsConstructor
public class ChatAssistantService {

  private static final Logger log = LoggerFactory.getLogger(ChatAssistantService.class);

  private static final String FALLBACK_ANSWER_EMPTY =
      "I couldn't understand the question — please rephrase.";
  private static final String FALLBACK_ANSWER_PROVIDER_ERROR =
      "The assistant is temporarily unavailable. Please try again shortly.";

  private final ChatClient chatClient;
  private final ChatRateLimiter rateLimiter;
  private final ChatboxProperties properties;
  private final CommandValidator commandValidator;
  private final Clock clock;

  /**
   * Executes the ask flow.
   *
   * @param command the validated user input
   * @return the synthesised {@link AskResult}
   * @throws at.mavila.dbchatbox.domain.chatbox.exception.ChatRateLimitExceededException
   *         when the per-hour limit is exceeded
   * @throws ChatProviderUnavailableException when the LLM call fails
   */
  @Transactional(readOnly = true)
  public AskResult ask(final AskCommand command) {
    commandValidator.validate(command);
    rateLimiter.checkAllowed();

    final long startMillis = clock.millis();
    final String prompt = command.prompt();
    final String locale = nonNull(command.locale()) ? command.locale() : "en-US";

    log.info("chatbox.ask prompt.length={} locale={}", prompt.length(), locale);

    final ChatResponse chatResponse = callProvider(prompt, locale);
    final String answer = extractAnswer(chatResponse);
    final int latencyMillis = (int) (clock.millis() - startMillis);

    log.info("chatbox.ask.ok latency={}ms tokens.prompt={} tokens.completion={}",
        latencyMillis, promptTokens(chatResponse), completionTokens(chatResponse));

    return new AskResult(
        answer,
        List.of(),
        modelId(chatResponse),
        promptTokens(chatResponse),
        completionTokens(chatResponse),
        latencyMillis);
  }

  /**
   * Sends the prompt through the pre-configured {@link ChatClient}. Spring AI
   * handles the entire tool-calling loop internally: it serialises tool
   * arguments / results as JSON, dispatches tool calls to our Spring beans,
   * and only returns when the LLM emits its final text (or a recoverable
   * error).
   */
  private ChatResponse callProvider(final String prompt, final String locale) {
    try {
      return chatClient.prompt()
          .user(u -> u.text("[locale={locale}] {prompt}")
              .param("locale", locale)
              .param("prompt", prompt))
          .call()
          .chatResponse();
    } catch (final RuntimeException ex) {
      log.error("chatbox.ask.provider_error model={} message={}",
          properties.getModel(), ex.getMessage(), ex);
      throw new ChatProviderUnavailableException(FALLBACK_ANSWER_PROVIDER_ERROR, ex);
    }
  }

  /**
   * Pulls the answer text out of the {@link ChatResponse}. When the provider
   * returns no generation (should not happen, but is legal per the API
   * contract), we fall back to a generic apology rather than surfacing
   * a blank string.
   */
  private String extractAnswer(final ChatResponse chatResponse) {
    if (isNull(chatResponse)) {
      return FALLBACK_ANSWER_EMPTY;
    }
    final Generation generation = chatResponse.getResult();
    if (isNull(generation) || isNull(generation.getOutput())) {
      return FALLBACK_ANSWER_EMPTY;
    }
    final String text = generation.getOutput().getText();
    if (isNull(text) || text.isBlank()) {
      return FALLBACK_ANSWER_EMPTY;
    }
    return text;
  }

  private String modelId(final ChatResponse response) {
    if (isNull(response) || isNull(response.getMetadata())) {
      return properties.getModel();
    }
    final String reported = response.getMetadata().getModel();
    return nonNull(reported) && !reported.isBlank() ? reported : properties.getModel();
  }

  private Integer promptTokens(final ChatResponse response) {
    final Usage usage = usage(response);
    return nonNull(usage) && nonNull(usage.getPromptTokens()) ? usage.getPromptTokens().intValue() : null;
  }

  private Integer completionTokens(final ChatResponse response) {
    final Usage usage = usage(response);
    return nonNull(usage) && nonNull(usage.getCompletionTokens())
        ? usage.getCompletionTokens().intValue()
        : null;
  }

  private Usage usage(final ChatResponse response) {
    if (isNull(response)) {
      return null;
    }
    final ChatResponseMetadata metadata = response.getMetadata();
    return nonNull(metadata) ? metadata.getUsage() : null;
  }
}
