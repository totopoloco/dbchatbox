/**
 * Spring AI wiring — everything that bridges between the chatbox domain and
 * the Spring AI framework (the {@code ChatClient}, system prompt, and clock).
 *
 * <p>
 * Kept in {@code infrastructure} so the domain layer never imports Spring AI
 * types directly: {@code ChatAssistantService} depends on the
 * {@link org.springframework.ai.chat.client.ChatClient} interface and on
 * {@code ChatboxProperties} (in the domain package), but the concrete bean
 * graph — which model, which tools, which system prompt — is assembled here.
 * </p>
 *
 * @since 2026-04-20
 */
package at.mavila.dbchatbox.infrastructure.ai;
