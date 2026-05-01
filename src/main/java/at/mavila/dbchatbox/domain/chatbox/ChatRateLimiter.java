package at.mavila.dbchatbox.domain.chatbox;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.stereotype.Component;

import at.mavila.dbchatbox.domain.chatbox.exception.ChatRateLimitExceededException;
import lombok.RequiredArgsConstructor;

/**
 * Very simple global rate limiter for the chatbox.
 *
 * <p>
 * <strong>Design note — Phase 1:</strong> no authentication exists yet, so
 * there
 * is no per-caller identity. We therefore apply a single <em>global</em>
 * sliding-window limit across all callers. When authentication is introduced
 * in Phase 2, this class can be replaced by a per-principal limiter (Bucket4j,
 * Resilience4j, Redis token bucket, …) without changing the
 * {@link ChatAssistantService} contract.
 * </p>
 *
 * <p>
 * The limiter records the timestamp of each accepted request in a bounded
 * deque. On each {@link #checkAllowed()} call, expired timestamps (older than
 * the configured window) are drained from the head; if the remaining count is
 * below the limit, the new request is accepted and its timestamp pushed onto
 * the tail. Otherwise a {@link ChatRateLimitExceededException} is thrown.
 * </p>
 *
 * <p>
 * Thread-safety is provided by a single {@code synchronized} block around the
 * check/record step. This is adequate because the chatbox endpoint is
 * latency-dominated by the LLM round-trip (hundreds of ms); lock contention on
 * a microsecond-level operation is negligible.
 * </p>
 *
 * @since 2026-04-20
 */
@Component
@RequiredArgsConstructor
public class ChatRateLimiter {

  private final ChatboxProperties properties;
  private final Clock clock;

  private final Deque<Long> requestTimestamps = new ArrayDeque<>();

  /**
   * Checks whether a new request is allowed under the current window and,
   * if so, records its timestamp. Must be called exactly once per
   * {@code ask} invocation, before the LLM is contacted.
   *
   * @throws ChatRateLimitExceededException when the limit has been reached
   */
  public void checkAllowed() {
    final long now = clock.millis();
    final long windowStart = now - Duration.ofHours(1).toMillis();
    final int limit = properties.getRateLimit().getRequestsPerHour();

    synchronized (this.requestTimestamps) {

      // Evict expired timestamps from the head of the deque until either the
      // deque is empty or the oldest timestamp falls within the current window.
      while (isOldestTimestampExpired(windowStart)) {
        this.requestTimestamps.pollFirst();
      }

      // If the deque size is below the limit, accept the request and record its
      // timestamp at the tail. Otherwise, reject with an exception.
      if (this.requestTimestamps.size() >= limit) {
        throw new ChatRateLimitExceededException(
            "Rate limit exceeded: %d requests per hour.".formatted(limit));
      }

      // Record the new request's timestamp at the tail of the deque.
      // So the deque looks like this: [oldest, ..., newest], and we evict from the
      // head and add to the tail.
      this.requestTimestamps.addLast(now);
    }
  }

  /**
   * Returns {@code true} when the deque is non-empty and its oldest entry
   * pre-dates {@code windowStart}, meaning that entry has left the sliding
   * window and should be evicted.
   *
   * @param windowStart epoch-millisecond timestamp marking the beginning of
   *                    the current window (typically {@code now − 1 hour})
   * @return {@code true} if the oldest timestamp has expired and must be
   *         removed; {@code false} if the deque is empty or the oldest entry
   *         still falls within the window
   */
  private boolean isOldestTimestampExpired(final long windowStart) {
    return !this.requestTimestamps.isEmpty() && (this.requestTimestamps.peekFirst() < windowStart);
  }

  /**
   * Test-only helper — clears the window so test cases do not leak into
   * each other. Not part of the production contract; production code must
   * never call this.
   */
  public void reset() {
    synchronized (this.requestTimestamps) {
      this.requestTimestamps.clear();
    }
  }
}
