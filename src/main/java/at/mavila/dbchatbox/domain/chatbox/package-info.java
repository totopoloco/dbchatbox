/**
 * Natural-language chat assistant.
 *
 * <p>
 * This package hosts the domain layer of the "chatbox" feature. A single GraphQL
 * query — {@code ask(input: AskInput!): AskResult!} — accepts a free-form prompt
 * and returns a synthesised answer. The actual data lookups are performed by
 * LLM <em>tool calls</em>: the Large Language Model (configured via Spring AI)
 * decides which Java method to invoke, Spring AI dispatches the call, and the
 * method delegates to an existing domain service in {@code domain.club.*}.
 * </p>
 *
 * <p>
 * <strong>Phase 1 scope:</strong> read-only tools, no conversation memory,
 * no security / role-based tool gating (will be added in Phase 2). A simple
 * global rate limiter guards against runaway LLM costs.
 * </p>
 *
 * <p>
 * Key classes:
 * </p>
 *
 * <ul>
 *   <li>{@link at.mavila.dbchatbox.domain.chatbox.AskCommand} — validated input
 *       record (Jakarta Bean Validation).</li>
 *   <li>{@link at.mavila.dbchatbox.domain.chatbox.AskResult} — response DTO returned
 *       to the GraphQL controller.</li>
 *   <li>{@link at.mavila.dbchatbox.domain.chatbox.ChatAssistantService} — orchestrates
 *       the rate-limit check, {@code ChatClient} invocation, and result assembly.</li>
 *   <li>{@link at.mavila.dbchatbox.domain.chatbox.ChatRateLimiter} — simple
 *       sliding-window counter that short-circuits excess traffic before the LLM
 *       round-trip.</li>
 * </ul>
 *
 * <p>
 * The tool wrappers live in {@code domain.chatbox.tools} and are plain
 * {@code @Component}s whose methods carry {@code @Tool} annotations.
 * </p>
 *
 * @since 2026-04-20
 */
package at.mavila.dbchatbox.domain.chatbox;
