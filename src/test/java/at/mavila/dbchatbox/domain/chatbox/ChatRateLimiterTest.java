package at.mavila.dbchatbox.domain.chatbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import at.mavila.dbchatbox.domain.chatbox.exception.ChatRateLimitExceededException;

/**
 * Unit test for {@link ChatRateLimiter}.
 *
 * <p>
 * Uses a fixed {@link Clock} that the test advances manually, so we can assert
 * the sliding-window behaviour without relying on wall-clock timing.
 * </p>
 */
class ChatRateLimiterTest {

  private ChatboxProperties properties;
  private MutableClock clock;
  private ChatRateLimiter limiter;

  @BeforeEach
  void setUp() {
    properties = new ChatboxProperties();
    properties.getRateLimit().setRequestsPerHour(3);
    clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
    limiter = new ChatRateLimiter(properties, clock);
  }

  @Test
  void shouldAcceptRequestsUpToLimit() {
    IntStream.range(0, 3).forEach(i -> limiter.checkAllowed());

    assertThatThrownBy(() -> limiter.checkAllowed())
        .isInstanceOf(ChatRateLimitExceededException.class)
        .hasMessageContaining("3 requests per hour");
  }

  @Test
  void shouldReleaseCapacityAfterWindowExpires() {
    limiter.checkAllowed();
    limiter.checkAllowed();
    limiter.checkAllowed();

    clock.advanceMinutes(30);
    assertThatThrownBy(() -> limiter.checkAllowed())
        .isInstanceOf(ChatRateLimitExceededException.class);

    clock.advanceMinutes(31);
    // The three original timestamps are now > 1 hour old → window is empty.
    limiter.checkAllowed();
    limiter.checkAllowed();
    limiter.checkAllowed();

    assertThatThrownBy(() -> limiter.checkAllowed())
        .isInstanceOf(ChatRateLimitExceededException.class);
  }

  @Test
  void resetClearsInternalState() {
    limiter.checkAllowed();
    limiter.checkAllowed();
    limiter.checkAllowed();
    limiter.reset();

    // Same instant — after reset, we can fill the window again immediately.
    assertThat(clock.millis()).isEqualTo(Instant.parse("2026-04-20T10:00:00Z").toEpochMilli());
    limiter.checkAllowed();
    limiter.checkAllowed();
    limiter.checkAllowed();
  }

  /**
   * Fixed-instant {@link Clock} whose time can be advanced explicitly.
   */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(final Instant start) {
      this.now = start;
    }

    void advanceMinutes(final int minutes) {
      this.now = this.now.plusSeconds(minutes * 60L);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
