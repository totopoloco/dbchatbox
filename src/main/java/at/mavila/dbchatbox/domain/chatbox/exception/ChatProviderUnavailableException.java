package at.mavila.dbchatbox.domain.chatbox.exception;

/**
 * Thrown when the upstream LLM provider (Anthropic / OpenAI / …) cannot be
 * reached or returns a non-recoverable error.
 *
 * <p>
 * Wraps the underlying exception as its cause so operators can diagnose from
 * logs without exposing provider internals to GraphQL clients. The
 * {@code GraphQlExceptionAdvice} surfaces only the exception message —
 * a generic "assistant is temporarily unavailable" string.
 * </p>
 *
 * @since 2026-04-20
 */
public class ChatProviderUnavailableException extends RuntimeException {

  /**
   * Creates a new provider-unavailable exception.
   *
   * @param message generic user-facing message
   * @param cause   the underlying provider / I/O exception (for logs only)
   */
  public ChatProviderUnavailableException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
