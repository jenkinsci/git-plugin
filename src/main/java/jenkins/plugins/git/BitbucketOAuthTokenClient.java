package jenkins.plugins.git;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.json.JSONObject;

/**
 * Exchanges Bitbucket OAuth consumer credentials for short-lived access tokens.
 */
final class BitbucketOAuthTokenClient {

    private static final URI TOKEN_ENDPOINT = URI.create("https://bitbucket.org/site/oauth2/access_token");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);
    private static final Map<String, CachedToken> TOKENS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private BitbucketOAuthTokenClient() {
        // Utility class.
    }

    static String accessToken(String credentialsId, String clientKey, String clientSecret) {
        String cacheKey = cacheKey(credentialsId, clientKey, clientSecret);
        CachedToken cached = TOKENS.get(cacheKey);
        if (cached != null && cached.isUsable()) {
            return cached.value;
        }

        synchronized (TOKENS) {
            cached = TOKENS.get(cacheKey);
            if (cached != null && cached.isUsable()) {
                return cached.value;
            }
            CachedToken refreshed = requestToken(clientKey, clientSecret);
            TOKENS.put(cacheKey, refreshed);
            return refreshed.value;
        }
    }

    private static CachedToken requestToken(String clientKey, String clientSecret) {
        String basicCredential = Base64.getEncoder().encodeToString(
                (clientKey + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(TOKEN_ENDPOINT)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Basic " + basicCredential)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException(
                        "Bitbucket OAuth token request failed with HTTP " + response.statusCode());
            }

            JSONObject json = JSONObject.fromObject(response.body());
            String token = json.optString("access_token", "");
            if (token.isBlank()) {
                throw new IllegalArgumentException("Bitbucket OAuth response did not include an access token");
            }
            long expiresIn = json.optLong("expires_in", 7200L);
            return new CachedToken(token, Instant.now().plusSeconds(expiresIn));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to contact the Bitbucket OAuth token endpoint", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Interrupted while requesting a Bitbucket OAuth token", exception);
        }
    }

    private static String cacheKey(String credentialsId, String clientKey, String clientSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(
                    (credentialsId + '\0' + clientKey + '\0' + clientSecret).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class CachedToken {
        private final String value;
        private final Instant expiresAt;

        private CachedToken(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        private boolean isUsable() {
            return Instant.now().plus(EXPIRY_MARGIN).isBefore(expiresAt);
        }
    }
}
