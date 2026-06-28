package at.mavila.dbchatbox.infrastructure.web.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import at.mavila.dbchatbox.TenantAwareIntegrationTest;
import at.mavila.dbchatbox.domain.chatbox.ChatRateLimiter;

/**
 * GraphQL-level integration test for the chatbox.
 *
 * <h2>Setup</h2>
 *
 * <ul>
 *   <li>The real Spring context loads (H2 + Flyway + GraphQL + Spring AI
 *       auto-config).</li>
 *   <li>The {@link ChatClient} bean produced by {@code ChatClientConfiguration}
 *       is replaced by a Mockito mock ({@code @MockitoBean}). No real HTTP
 *       call to Anthropic is made — our mock returns a canned
 *       {@link ChatResponse} containing a stubbed answer.</li>
 *   <li>{@code spring.ai.anthropic.api-key=test-only} keeps the Anthropic
 *       auto-config happy at startup so the {@code ChatClient.Builder} bean
 *       can be created; our {@code @MockitoBean} overrides the final
 *       {@code ChatClient}, so the dummy key is never actually used.</li>
 *   <li>{@code app.chatbox.rate-limit.requests-per-hour=2} lets us exercise
 *       the rate-limit path without flooding.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureHttpGraphQlTester
@TestPropertySource(properties = {
    "spring.ai.anthropic.api-key=test-only",
    "app.chatbox.rate-limit.requests-per-hour=2"
})
class ChatAssistantControllerIntegrationTest extends TenantAwareIntegrationTest {

  @Autowired
  private HttpGraphQlTester graphQlTester;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  private ChatClient chatClient;

  @Autowired
  private ChatRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    rateLimiter.reset();

    final AssistantMessage message = new AssistantMessage("Stubbed assistant reply.");
    final Generation generation = new Generation(message);
    final ChatResponse response = new ChatResponse(List.of(generation));

    when(chatClient.prompt().user(any(Consumer.class)).call().chatResponse())
        .thenReturn(response);
  }

  @Test
  void shouldReturnAnswerForValidPrompt() {
    graphQlTester.document("""
        query {
          ask(input: { prompt: "who hasn't paid?" }) {
            answer
            model
            latencyMillis
          }
        }
        """).execute()
        .path("ask.answer").entity(String.class).isEqualTo("Stubbed assistant reply.")
        .path("ask.latencyMillis").entity(Integer.class).satisfies(v -> assertThat(v).isGreaterThanOrEqualTo(0));
  }

  @Test
  void shouldReturnValidationErrorWhenPromptIsBlank() {
    graphQlTester.document("""
        query {
          ask(input: { prompt: "" }) {
            answer
          }
        }
        """).execute()
        .errors().satisfy(errors -> assertThat(errors).isNotEmpty());
  }

  @Test
  void shouldSurfaceRateLimitErrorAfterLimitExceeded() {
    // limit = 2, so the first two succeed, the third is rate-limited
    for (int i = 0; i < 2; i++) {
      graphQlTester.document("""
          query { ask(input: { prompt: "ping" }) { answer } }
          """).execute().errors().verify();
    }

    graphQlTester.document("""
        query { ask(input: { prompt: "ping" }) { answer } }
        """).execute()
        .errors()
        .satisfy(errors -> {
          assertThat(errors).hasSize(1);
          assertThat(errors.get(0).getMessage()).contains("Rate limit");
        });
  }
}
