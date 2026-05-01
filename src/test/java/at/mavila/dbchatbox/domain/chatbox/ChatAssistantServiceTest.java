package at.mavila.dbchatbox.domain.chatbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import at.mavila.dbchatbox.domain.chatbox.exception.ChatProviderUnavailableException;
import at.mavila.dbchatbox.domain.chatbox.exception.ChatRateLimitExceededException;
import at.mavila.dbchatbox.domain.support.CommandValidator;

/**
 * Unit test for {@link ChatAssistantService}.
 *
 * <p>
 * The {@link ChatClient} is mocked with deep stubs (Answers.RETURNS_DEEP_STUBS)
 * because its fluent API has four levels
 * ({@code prompt().user(...).call().chatResponse()}) and stubbing each layer
 * explicitly would be more noise than signal.
 * </p>
 *
 * <p>
 * For reviewers new to Spring AI: the mocked {@code chatClient.prompt()} call
 * returns a request spec, then {@code .user(...)} sets the user message,
 * {@code .call()} triggers the (mocked) LLM invocation, and
 * {@code .chatResponse()} returns the {@link ChatResponse} we control here.
 * No real HTTP call to Anthropic is made.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ChatAssistantServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient chatClient;

  @Mock
  private ChatRateLimiter rateLimiter;

  @Mock
  private CommandValidator commandValidator;

  private ChatboxProperties properties;
  private Clock clock;
  private ChatAssistantService service;

  @BeforeEach
  void setUp() {
    properties = new ChatboxProperties();
    clock = Clock.fixed(Instant.parse("2026-04-20T10:00:00Z"), ZoneOffset.UTC);
    service = new ChatAssistantService(chatClient, rateLimiter, properties, commandValidator, clock);
  }

  @Test
  void shouldReturnAnswerWhenLlmRespondsNormally() {
    final Usage usage = new DefaultUsage(120, 45);
    final ChatResponseMetadata metadata = ChatResponseMetadata.builder()
        .model("claude-haiku-4-5-20251001")
        .usage(usage)
        .build();
    final AssistantMessage message = new AssistantMessage("Three members have unpaid subscriptions.");
    final Generation generation = new Generation(message);
    final ChatResponse response = new ChatResponse(java.util.List.of(generation), metadata);

    stubChatClient(response);

    final AskResult result = service.ask(new AskCommand("who hasn't paid?", "en-US"));

    assertThat(result.answer()).isEqualTo("Three members have unpaid subscriptions.");
    assertThat(result.model()).isEqualTo("claude-haiku-4-5-20251001");
    assertThat(result.promptTokens()).isEqualTo(120);
    assertThat(result.completionTokens()).isEqualTo(45);
    assertThat(result.latencyMillis()).isGreaterThanOrEqualTo(0);
    assertThat(result.toolCalls()).isEmpty();

    verify(commandValidator).validate(any(AskCommand.class));
    verify(rateLimiter).checkAllowed();
  }

  @Test
  void shouldReturnFallbackAnswerWhenLlmReturnsEmptyText() {
    final AssistantMessage message = new AssistantMessage("   ");
    final Generation generation = new Generation(message);
    final ChatResponse response = new ChatResponse(java.util.List.of(generation));

    stubChatClient(response);

    final AskResult result = service.ask(new AskCommand("something opaque", null));

    assertThat(result.answer()).isEqualTo("I couldn't understand the question — please rephrase.");
  }

  @Test
  void shouldFallbackToConfiguredModelWhenProviderMetadataIsAbsent() {
    final AssistantMessage message = new AssistantMessage("ok");
    final Generation generation = new Generation(message);
    // new ChatResponse(List) produces a metadata object with empty/zero values,
    // not a null metadata. The service falls back to the configured model id
    // when the provider doesn't report one; tokens may legitimately surface as
    // zero when the provider includes an empty Usage.
    final ChatResponse response = new ChatResponse(java.util.List.of(generation));

    stubChatClient(response);

    final AskResult result = service.ask(new AskCommand("hi", null));

    assertThat(result.model()).isEqualTo(properties.getModel());
    // Tokens are either null (no Usage object) or zero (empty Usage) — both are acceptable
    assertThat(result.promptTokens()).satisfiesAnyOf(
        v -> assertThat(v).isNull(),
        v -> assertThat(v).isZero());
    assertThat(result.completionTokens()).satisfiesAnyOf(
        v -> assertThat(v).isNull(),
        v -> assertThat(v).isZero());
  }

  @Test
  void shouldWrapProviderExceptionInChatProviderUnavailableException() {
    when(chatClient.prompt().user(any(Consumer.class)).call().chatResponse())
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.ask(new AskCommand("anything", null)))
        .isInstanceOf(ChatProviderUnavailableException.class)
        .hasMessageContaining("temporarily unavailable")
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldNotInvokeChatClientWhenRateLimitExceeded() {
    final ChatRateLimitExceededException rateLimit =
        new ChatRateLimitExceededException("Rate limit exceeded");
    org.mockito.Mockito.doThrow(rateLimit).when(rateLimiter).checkAllowed();

    assertThatThrownBy(() -> service.ask(new AskCommand("hi", null)))
        .isInstanceOf(ChatRateLimitExceededException.class);

    verify(chatClient, never()).prompt();
  }

  @Test
  void shouldValidateCommandBeforeCallingLlm() {
    final IllegalArgumentException invalid = new IllegalArgumentException("prompt blank");
    org.mockito.Mockito.doThrow(invalid).when(commandValidator).validate(any());

    assertThatThrownBy(() -> service.ask(new AskCommand("", null)))
        .isInstanceOf(IllegalArgumentException.class);

    verify(rateLimiter, never()).checkAllowed();
    verify(chatClient, never()).prompt();
    verify(commandValidator, times(1)).validate(any());
  }

  private void stubChatClient(final ChatResponse response) {
    when(chatClient.prompt().user(any(Consumer.class)).call().chatResponse())
        .thenReturn(response);
  }
}
