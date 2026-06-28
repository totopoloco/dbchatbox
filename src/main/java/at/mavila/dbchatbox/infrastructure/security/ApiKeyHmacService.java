package at.mavila.dbchatbox.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Computes HMAC-SHA256 digests of raw API keys using the pepper configured via
 * {@link ApiKeyProperties#getHmacSecret()}.
 *
 * <p>A stolen database row is useless without the pepper: the attacker cannot
 * reverse the hash or forge new keys. The pepper must never be committed to version
 * control (rule 91) — it must come from an environment variable or secrets manager.</p>
 *
 * <p>Thread safety: a new {@link Mac} instance is created per call because the JCA
 * {@code Mac} class is not thread-safe.</p>
 *
 * @since 2026-06-28
 */
@Component
@RequiredArgsConstructor
public class ApiKeyHmacService {

    private static final String ALGORITHM = "HmacSHA256";

    private final ApiKeyProperties apiKeyProperties;

    /**
     * Computes the HMAC-SHA256 of {@code rawKey} using the configured secret and encodes
     * the result as standard Base64 (URL-unsafe, padded — suitable for DB storage).
     *
     * @param rawKey the plaintext API key returned to the user
     * @return base64-encoded HMAC digest (44 characters for SHA-256)
     * @throws IllegalStateException if HMAC-SHA256 is unavailable or the key is invalid
     *                               (neither should happen in a conformant JVM)
     */
    public String hmac(final String rawKey) {
        try {
            final byte[] secretBytes = apiKeyProperties.getHmacSecret().getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec keySpec = new SecretKeySpec(secretBytes, ALGORITHM);
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Failed to compute API key HMAC", ex);
        }
    }
}
