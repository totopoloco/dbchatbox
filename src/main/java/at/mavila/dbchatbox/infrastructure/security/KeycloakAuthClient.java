package at.mavila.dbchatbox.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import at.mavila.dbchatbox.domain.club.exception.InvalidCredentialsException;

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
    private static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";
    private static final String PARAM_GRANT_TYPE = "grant_type";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_SECRET = "client_secret";
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
        form.add(PARAM_GRANT_TYPE, GRANT_PASSWORD);
        form.add(PARAM_CLIENT_ID, props.getSpaClientId());
        form.add("username", username);
        form.add("password", password);
        try {
            return exchange(realm, form);
        } catch (final HttpClientErrorException.Unauthorized ex) {
            log.warn("Login rejected by Keycloak for user '{}' in realm '{}'", username, realm);
            throw new InvalidCredentialsException("Invalid username or password");
        }
    }

    /**
     * Obtains a service-account access token for the given realm using the
     * {@code client_credentials} grant. Used by the API-key authentication path so
     * Keycloak Admin API calls can proceed without a user JWT.
     *
     * @param realm        the Keycloak realm name
     * @param clientId     the M2M client ID (e.g. {@code club-m2m})
     * @param clientSecret the M2M client secret stored in {@link at.mavila.dbchatbox.domain.club.tenant.Tenant}
     * @return the service-account auth payload (only {@code accessToken} is populated)
     */
    public AuthPayload clientCredentials(final String realm, final String clientId, final String clientSecret) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(PARAM_GRANT_TYPE, GRANT_CLIENT_CREDENTIALS);
        form.add(PARAM_CLIENT_ID, clientId);
        form.add(PARAM_CLIENT_SECRET, clientSecret);
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
        form.add(PARAM_GRANT_TYPE, GRANT_REFRESH);
        form.add(PARAM_CLIENT_ID, props.getSpaClientId());
        form.add("refresh_token", refreshToken);
        try {
            return exchange(realm, form);
        } catch (final HttpClientErrorException.Unauthorized ex) {
            log.warn("Token refresh rejected by Keycloak in realm '{}'", realm);
            throw new InvalidCredentialsException("Refresh token is invalid or has expired");
        }
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
