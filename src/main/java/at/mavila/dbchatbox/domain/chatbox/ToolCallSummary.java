package at.mavila.dbchatbox.domain.chatbox;

/**
 * Summary of a single tool call performed by the LLM while producing an answer.
 *
 * <p>
 * Returned as part of {@link AskResult#toolCalls()} so the frontend (or a
 * reviewer) can inspect what underlying operations the assistant invoked.
 * The payload is intentionally light — the full tool result is NOT surfaced
 * here, both to keep GraphQL responses compact and to avoid leaking raw
 * personal data that the LLM has already summarised in its answer.
 * </p>
 *
 * @param name           the tool method name (e.g. {@code outstandingPayments})
 * @param arguments      JSON string of the arguments the LLM passed to the tool
 *                       (may be empty {@code {}} for no-argument tools)
 * @param durationMillis how long the tool took to execute on the server
 * @param error          the domain-exception message if the tool threw; {@code null} on success
 * @since 2026-04-20
 */
public record ToolCallSummary(
    String name,
    String arguments,
    int durationMillis,
    String error) {
}
