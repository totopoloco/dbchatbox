package at.mavila.dbchatbox.domain.chatbox.exception;

/**
 * Thrown when the chatbox rate limit is exceeded for the current window.
 *
 * <p>
 * Mapped in {@code GraphQlExceptionAdvice} to a GraphQL error with
 * {@code classification: RATE_LIMITED}. The LLM is never invoked when this
 * exception is thrown — the rate check happens before the provider call.
 * </p>
 *
 * @since 2026-04-20
 */
public class ChatRateLimitExceededException extends RuntimeException {

  /**
   * Creates a new rate-limit exception.
   *
   * @param message human-readable explanation including the configured limit
   */
  public ChatRateLimitExceededException(final String message) {
    super(message);
  }
}
