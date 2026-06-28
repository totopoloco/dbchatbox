package at.mavila.dbchatbox.infrastructure.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import at.mavila.dbchatbox.TenantAwareIntegrationTest;

/**
 * Integration tests for CORS configuration applied to the GraphQL endpoint.
 *
 * <p>
 * Tests send HTTP preflight ({@code OPTIONS}) requests with an {@code Origin}
 * header and
 * assert that Spring returns the correct CORS response headers for allowed
 * origins and
 * rejects requests from disallowed ones.
 * </p>
 *
 * @see CorsWebConfiguration
 * @see CorsProperties
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.ai.anthropic.api-key=test-only",
        "app.cors.allowed-origins=http://localhost:3000"
})
class CorsWebConfigurationTest extends TenantAwareIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Nested
    class GraphQlEndpoint {

        @Test
        void preflightFromAllowedOrigin_returnsOkWithCorsHeader() {
            webTestClient.options().uri("/graphql")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
        }

        @Test
        void preflightFromDisallowedOrigin_returnsForbidden() {
            webTestClient.options().uri("/graphql")
                    .header("Origin", "http://evil.example.com")
                    .header("Access-Control-Request-Method", "POST")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().doesNotExist("Access-Control-Allow-Origin");
        }

    }

    @Nested
    class GraphiqlEndpoint {

        @Test
        void preflightFromAllowedOrigin_returnsOkWithCorsHeader() {
            webTestClient.options().uri("/graphiql")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "GET")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
        }

        @Test
        void preflightFromDisallowedOrigin_returnsForbidden() {
            webTestClient.options().uri("/graphiql")
                    .header("Origin", "http://evil.example.com")
                    .header("Access-Control-Request-Method", "GET")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().doesNotExist("Access-Control-Allow-Origin");
        }

    }

}
