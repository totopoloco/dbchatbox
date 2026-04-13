package at.mavila.dbchatbox.infrastructure.web.graphql;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

import at.mavila.dbchatbox.domain.club.exception.DuplicateEmailException;
import at.mavila.dbchatbox.domain.club.exception.DuplicateNameException;
import at.mavila.dbchatbox.domain.club.exception.InvalidOperationException;
import at.mavila.dbchatbox.domain.club.exception.InvalidStatusTransitionException;
import at.mavila.dbchatbox.domain.club.exception.MemberDeletedException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.exception.OverlapException;
import at.mavila.dbchatbox.domain.club.exception.ResourceNotFoundException;
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
}
