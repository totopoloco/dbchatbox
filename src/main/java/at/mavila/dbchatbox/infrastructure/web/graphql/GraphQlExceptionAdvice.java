package at.mavila.dbchatbox.infrastructure.web.graphql;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

import at.mavila.dbchatbox.domain.chatbox.exception.ChatProviderUnavailableException;
import at.mavila.dbchatbox.domain.chatbox.exception.ChatRateLimitExceededException;
import at.mavila.dbchatbox.domain.club.exception.DuplicateEmailException;
import at.mavila.dbchatbox.domain.club.exception.DuplicateNameException;
import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.InvalidStatusTransitionException;
import at.mavila.dbchatbox.domain.club.exception.MemberDeletedException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.exception.OverlapException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;

/**
 * Global GraphQL exception handler that maps domain exceptions to meaningful
 * GraphQL errors.
 *
 * @since 2026-04-09
 */
@ControllerAdvice
public class GraphQlExceptionAdvice {

  /**
   * Handles not-found exceptions as {@code DataFetchingException}.
   *
   * @param ex  the not-found exception
   * @param env the data-fetching environment
   * @return a GraphQL error with the exception message
   */
  @GraphQlExceptionHandler({ MemberNotFoundException.class, ResourceNotFoundException.class })
  public GraphQLError handleNotFound(final RuntimeException ex, final DataFetchingEnvironment env) {
    return GraphQLError.newError().message(ex.getMessage()).errorType(graphql.ErrorType.DataFetchingException)
        .path(env.getExecutionStepInfo().getPath()).build();
  }

  /**
   * Handles duplicate-name and duplicate-email exceptions as
   * {@code ValidationError}.
   *
   * @param ex  the duplicate exception
   * @param env the data-fetching environment
   * @return a GraphQL error with the exception message
   */
  @GraphQlExceptionHandler({ DuplicateEmailException.class, DuplicateNameException.class })
  public GraphQLError handleDuplicate(final RuntimeException ex, final DataFetchingEnvironment env) {
    return GraphQLError.newError().message(ex.getMessage()).errorType(graphql.ErrorType.ValidationError)
        .path(env.getExecutionStepInfo().getPath()).build();
  }

  /**
   * Handles deleted-member access attempts as {@code DataFetchingException}.
   *
   * @param ex  the member-deleted exception
   * @param env the data-fetching environment
   * @return a GraphQL error with the exception message
   */
  @GraphQlExceptionHandler(MemberDeletedException.class)
  public GraphQLError handleDeleted(final MemberDeletedException ex, final DataFetchingEnvironment env) {
    return GraphQLError.newError().message(ex.getMessage()).errorType(graphql.ErrorType.DataFetchingException)
        .path(env.getExecutionStepInfo().getPath()).build();
  }

  /**
   * Handles invalid status transitions, invalid operations, and overlap errors as
   * {@code ValidationError}.
   *
   * @param ex  the bad-request exception
   * @param env the data-fetching environment
   * @return a GraphQL error with the exception message
   */
  @GraphQlExceptionHandler({ InvalidStatusTransitionException.class, InvalidOperationException.class,
      OverlapException.class })
  public GraphQLError handleBadRequest(final RuntimeException ex, final DataFetchingEnvironment env) {
    return GraphQLError.newError().message(ex.getMessage()).errorType(graphql.ErrorType.ValidationError)
        .path(env.getExecutionStepInfo().getPath()).build();
  }

  /**
   * Handles chatbox rate-limit exceptions with a custom {@code RATE_LIMITED}
   * classification so frontend clients can distinguish "please back off" from
   * generic validation errors.
   *
   * @param ex  the rate-limit exception
   * @param env the data-fetching environment
   * @return a GraphQL error with classification {@code RATE_LIMITED}
   */
  @GraphQlExceptionHandler(ChatRateLimitExceededException.class)
  public GraphQLError handleChatRateLimit(final ChatRateLimitExceededException ex,
      final DataFetchingEnvironment env) {
    return GraphQLError.newError().message(ex.getMessage())
        .errorType(ChatboxErrorClassification.RATE_LIMITED)
        .path(env.getExecutionStepInfo().getPath()).build();
  }

  /**
   * Handles upstream LLM provider failures. Surfaces the user-safe message
   * (the exception message is already a generic "temporarily unavailable"
   * string set by {@code ChatAssistantService}) without leaking provider
   * internals.
   *
   * @param ex  the provider-unavailable exception
   * @param env the data-fetching environment
   * @return a GraphQL error with classification {@code PROVIDER_UNAVAILABLE}
   */
  @GraphQlExceptionHandler(ChatProviderUnavailableException.class)
  public GraphQLError handleChatProvider(final ChatProviderUnavailableException ex,
      final DataFetchingEnvironment env) {
    return GraphQLError.newError().message(ex.getMessage())
        .errorType(ChatboxErrorClassification.PROVIDER_UNAVAILABLE)
        .path(env.getExecutionStepInfo().getPath()).build();
  }

  /**
   * Custom GraphQL error classifications for the chatbox feature. These are
   * surfaced under the {@code extensions.classification} field of each error
   * so the frontend can react differently (e.g. show a toast vs. retry).
   */
  private enum ChatboxErrorClassification implements ErrorClassification {
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE
  }
}
