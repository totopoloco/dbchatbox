package at.mavila.dbchatbox.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for Keycloak's OpenID Connect token endpoint.
 *
 * <p>Used only by {@link at.mavila.dbchatbox.infrastructure.web.graphql.AuthController}
 * to implement the {@code login} and {@code refreshToken} mutations. These mutations are
 * provided for the PoC data loader; production SPAs should use PKCE with direct
 * browser-to-Keycloak flows.</p>
 *
 * @since 2026-06-28
 */
@Component
@Slf4j
public class KeycloakAuthClient {

    private static final String GRANT_PASSWORD = "password";
    private static final String GRANT_REFRESH = "refresh_token";
    private static final String TOKEN_PATH = "/realms/{realm}/protocol/openid-connect/token";

    private final KeycloakProperties props;
    private final RestClient restClient;

    /** Package-private response record — never leaves this class. */
    private record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") int expiresIn,
        @JsonProperty("token_type") String tokenType
    ) {}

    public KeycloakAuthClient(final KeycloakProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    /**
     * Authenticates a user via Keycloak's resource-owner password credentials grant.
     *
     * @param realm    the Keycloak realm name (e.g. {@code wat-simmering})
     * @param username the user's Keycloak username
     * @param password the user's Keycloak password
     * @return the auth payload with access and refresh tokens
     */
    public AuthPayload login(final String realm, final String username, final String password) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_PASSWORD);
        form.add("client_id", props.getSpaClientId());
        form.add("username", username);
        form.add("password", password);
        return exchange(realm, form);
    }

    /**
     * Refreshes an access token using a Keycloak refresh token.
     *
     * @param realm        the Keycloak realm name
     * @param refreshToken the refresh token obtained from a previous {@code login} call
     * @return new access and refresh tokens
     */
    public AuthPayload refresh(final String realm, final String refreshToken) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_REFRESH);
        form.add("client_id", props.getSpaClientId());
        form.add("refresh_token", refreshToken);
        return exchange(realm, form);
    }

    private AuthPayload exchange(final String realm, final MultiValueMap<String, String> form) {
        final TokenResponse resp = restClient.post()
            .uri(TOKEN_PATH, realm)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);

        if (resp == null) {
            throw new IllegalStateException("Empty response from Keycloak token endpoint");
        }
        return new AuthPayload(resp.accessToken(), resp.refreshToken(), resp.expiresIn(), resp.tokenType());
    }
}
