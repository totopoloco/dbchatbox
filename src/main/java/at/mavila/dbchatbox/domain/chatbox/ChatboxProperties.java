package at.mavila.dbchatbox.domain.chatbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Strongly-typed configuration for the chatbox feature, populated from
 * {@code app.chatbox.*} properties at startup.
 *
 * <p>
 * <strong>Why a class, not a record?</strong> Because {@code @ConfigurationProperties}
 * binding with Jakarta Bean Validation on nested types works most reliably
 * with JavaBean-style getters/setters in Spring Boot 4. Using Lombok's
 * {@code @Getter}/{@code @Setter} keeps the boilerplate minimal.
 * </p>
 *
 * <p>
 * Constraint validation fires at startup when {@code @Validated} is present on
 * the class — the application refuses to start with non-sensical values
 * (e.g. negative rate limit).
 * </p>
 *
 * @since 2026-04-20
 */
@ConfigurationProperties(prefix = "app.chatbox")
@Validated
@Getter
@Setter
public class ChatboxProperties {

  /**
   * Model identifier — also configured at the Spring AI layer via
   * {@code spring.ai.anthropic.chat.options.model}. Duplicated here so we
   * can surface it as a fallback when the provider response does not include
   * a {@code model} field in its metadata.
   */
  @NotBlank
  private String model = "claude-haiku-4-5-20251001";

  @Valid
  private RateLimit rateLimit = new RateLimit();

  /**
   * Rate-limit settings. See {@link ChatRateLimiter}.
   */
  @Getter
  @Setter
  public static class RateLimit {

    /**
     * Maximum number of {@code ask} calls accepted per rolling hour across
     * all callers. Phase 1 is global because authentication does not exist
     * yet — Phase 2 will add a per-principal limit.
     */
    @Min(value = 1, message = "rate-limit.requests-per-hour must be at least 1")
    private int requestsPerHour = 30;
  }
}
