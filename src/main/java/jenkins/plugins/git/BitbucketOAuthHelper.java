package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.regex.Pattern;

/**
 * Helper utilities for Bitbucket OAuth-aware remote handling.
 *
 * <p>Bitbucket Cloud accepts OAuth access tokens for Git-over-HTTPS with the
 * {@value #OAUTH_USERNAME} username. The token is supplied to the Git client
 * as a password credential; it is never added to the configured remote URL.</p>
 */
public final class BitbucketOAuthHelper {

    static final String OAUTH_USERNAME = "x-token-auth";
    private static final int OAUTH_CLIENT_KEY_LENGTH = 18;
    private static final int OAUTH_CLIENT_SECRET_LENGTH = 32;

    private static final Pattern BITBUCKET_CLOUD_HOST = Pattern.compile(
            "^(?:(?:https?|ssh)://(?:[^@/]+@)?bitbucket\\.org(?:/|$)|[^@/:]+@bitbucket\\.org:.+)");
    private static final Pattern BITBUCKET_CLOUD_HTTPS_HOST = Pattern.compile(
            "^https://(?:[^@/]+@)?bitbucket\\.org(?:/|$)");

    private BitbucketOAuthHelper() {
        // Utility class.
    }

    /**
     * Returns true when the supplied remote target is a Bitbucket Cloud repository.
     */
    public static boolean isBitbucketCloudRemote(@CheckForNull String remote) {
        if (remote == null || remote.isBlank()) {
            return false;
        }
        return BITBUCKET_CLOUD_HOST.matcher(remote).find();
    }

    /**
     * Returns a transient Git transport credential when an HTTPS Bitbucket Cloud
     * remote is paired with OAuth consumer credentials.
     *
     * <p>Other providers, Bitbucket SSH remotes, app passwords, and credentials
     * that already contain an access token are returned unchanged. The configured
     * Git remote is never rewritten.</p>
     */
    @NonNull
    public static StandardUsernameCredentials credentialsFor(
            @CheckForNull String remote, @NonNull StandardUsernameCredentials credentials) {
        return credentialsFor(remote, credentials, BitbucketOAuthTokenClient::accessToken);
    }

    static StandardUsernameCredentials credentialsFor(
            @CheckForNull String remote,
            @NonNull StandardUsernameCredentials credentials,
            @NonNull OAuthTokenProvider tokenProvider) {
        if (!isBitbucketCloudHttpsRemote(remote)
                || !(credentials instanceof StandardUsernamePasswordCredentials usernamePassword)
                || !isOAuthConsumer(usernamePassword)) {
            return credentials;
        }
        try {
            String accessToken = tokenProvider.accessToken(
                    usernamePassword.getId(),
                    usernamePassword.getUsername(),
                    usernamePassword.getPassword().getPlainText());
            return new UsernamePasswordCredentialsImpl(
                    usernamePassword.getScope(),
                    usernamePassword.getId(),
                    usernamePassword.getDescription(),
                    OAUTH_USERNAME,
                    accessToken);
        } catch (hudson.model.Descriptor.FormException exception) {
            throw new IllegalArgumentException("Unable to create Bitbucket OAuth credential", exception);
        }
    }

    private static boolean isOAuthConsumer(StandardUsernamePasswordCredentials credentials) {
        // Keep this aligned with BitbucketOAuthCredentialMatcher in the
        // bitbucket-branch-source plugin. It distinguishes consumer key/secret
        // pairs from user credentials and app passwords.
        String clientKey = credentials.getUsername();
        String clientSecret = credentials.getPassword().getPlainText();
        return clientKey.length() == OAUTH_CLIENT_KEY_LENGTH
                && clientSecret.length() == OAUTH_CLIENT_SECRET_LENGTH
                && !clientKey.contains(".")
                && !clientKey.contains("@");
    }

    private static boolean isBitbucketCloudHttpsRemote(@CheckForNull String remote) {
        return remote != null && BITBUCKET_CLOUD_HTTPS_HOST.matcher(remote).find();
    }

    @FunctionalInterface
    interface OAuthTokenProvider {
        String accessToken(String credentialsId, String clientKey, String clientSecret);
    }

    /**
     * Returns the provider name used in user-facing messages.
     */
    @NonNull
    public static String providerName() {
        return "Bitbucket";
    }
}
