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

    private static final Pattern BITBUCKET_CLOUD_HOST = Pattern.compile(
            "^(?:(?:https?|ssh)://(?:[^@/]+@)?bitbucket\\.org(?:/|$)|[^@/:]+@bitbucket\\.org:.+)");

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
     * Returns a transient Git transport credential for a Bitbucket Cloud OAuth token.
     * The configured Git remote remains unchanged.
     */
    @NonNull
    public static StandardUsernameCredentials credentialsFor(
            @CheckForNull String remote, @NonNull StandardUsernameCredentials credentials) {
        if (!isBitbucketCloudRemote(remote) || !(credentials instanceof StandardUsernamePasswordCredentials usernamePassword)) {
            return credentials;
        }
        try {
            return new UsernamePasswordCredentialsImpl(
                    usernamePassword.getScope(),
                    usernamePassword.getId(),
                    usernamePassword.getDescription(),
                    OAUTH_USERNAME,
                    usernamePassword.getPassword().getPlainText());
        } catch (hudson.model.Descriptor.FormException exception) {
            throw new IllegalArgumentException("Unable to create Bitbucket OAuth credential", exception);
        }
    }

    /**
     * Returns the provider name used in user-facing messages.
     */
    @NonNull
    public static String providerName() {
        return "Bitbucket";
    }
}
