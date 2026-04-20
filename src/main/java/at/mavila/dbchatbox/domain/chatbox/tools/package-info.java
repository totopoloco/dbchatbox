/**
 * AI tool wrappers — adapters that expose existing domain services to the
 * LLM via Spring AI's tool-calling protocol.
 *
 * <h2>What is a "tool" here?</h2>
 *
 * <p>
 * Spring AI lets the LLM call Java methods during a chat turn: when the
 * model decides it needs data, it emits a structured tool-call request;
 * Spring AI dispatches it to the matching Java method; the return value is
 * JSON-ified and fed back to the model, which composes its natural-language
 * answer.
 * </p>
 *
 * <p>
 * Each class in this package is a plain {@code @Component} whose methods
 * carry {@link org.springframework.ai.tool.annotation.Tool @Tool}. The
 * {@code description} tells the LLM <em>when</em> to call the method —
 * invest in writing clear, specific descriptions. Parameters are described
 * with {@link org.springframework.ai.tool.annotation.ToolParam @ToolParam};
 * Spring AI generates the JSON input-schema from the parameter types
 * automatically.
 * </p>
 *
 * <h2>DTO discipline</h2>
 *
 * <p>
 * Tool methods <strong>never</strong> return raw JPA entities. Entities
 * carry lazy-loaded relationships and JPA-internal fields that either break
 * JSON serialisation or leak unnecessary data to the LLM (blowing the token
 * bill). Each tool defines a nested {@code public record} with exactly the
 * fields the LLM needs — flat, primitive-ish, no cycles — and maps the
 * entity to that record inside the tool method. This mirrors the existing
 * pattern of nested result records seen in
 * {@code PaymentService.OutstandingPaymentInfo},
 * {@code TrainerLogService.TrainerHoursSummary}, and
 * {@code MemberGdprService.DeleteMemberResult}.
 * </p>
 *
 * <h2>No security (Phase 1)</h2>
 *
 * <p>
 * Every tool in this package is registered with the single
 * {@code ChatClient} bean and is therefore callable regardless of who asks.
 * Phase 2 will introduce per-role {@code ChatClient} beans that each see
 * only a filtered tool set; no change to the tool methods themselves is
 * required.
 * </p>
 *
 * @since 2026-04-20
 */
package at.mavila.dbchatbox.domain.chatbox.tools;
