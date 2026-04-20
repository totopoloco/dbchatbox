package at.mavila.dbchatbox.domain.chatbox;

import java.util.List;

/**
 * Result of an {@code ask} invocation — everything the GraphQL controller
 * surfaces to the client after the LLM has produced its answer.
 *
 * <p>
 * The {@code answer} field is the user-facing text. The other fields are
 * optional metadata for debugging, cost tracking, and UI niceties
 * (e.g. showing a latency badge).
 * </p>
 *
 * <p>
 * Tokens are nullable because not every LLM provider exposes usage data, and
 * the field is populated by whatever {@code ChatClient} returns in
 * {@code ChatResponse.getMetadata().getUsage()}. When unavailable, the value
 * is left {@code null} rather than zero to distinguish "unknown" from "free".
 * </p>
 *
 * @param answer           final natural-language answer produced by the LLM
 * @param toolCalls        tools invoked during this call; empty list if the LLM answered directly
 * @param model            model identifier reported by the provider (e.g. {@code claude-haiku-4-5-20251001})
 * @param promptTokens     provider-reported prompt tokens, or {@code null} if unavailable
 * @param completionTokens provider-reported completion tokens, or {@code null} if unavailable
 * @param latencyMillis    end-to-end server latency (rate-limit check + LLM round-trip + tool calls)
 * @since 2026-04-20
 */
public record AskResult(
    String answer,
    List<ToolCallSummary> toolCalls,
    String model,
    Integer promptTokens,
    Integer completionTokens,
    int latencyMillis) {
}
