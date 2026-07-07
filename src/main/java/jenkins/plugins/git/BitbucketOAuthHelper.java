package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Helper utilities for Bitbucket OAuth-aware remote handling.
 *
 * <p>The Bitbucket Cloud API accepts an OAuth or app-password token via the
 * x-token-auth scheme. When a build runs against a Bitbucket Cloud repository,
 * the git remote can be rewritten to include that token in a way that keeps the
 * existing git transport behavior intact.</p>
 */
public final class BitbucketOAuthHelper {

    private static final Pattern BITBUCKET_CLOUD_HOST = Pattern.compile("^(?:https?|ssh)://(?:[^@/]+@)?bitbucket\\.org(?:/|$)");

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
     * Rewrites a Bitbucket Cloud remote to include an OAuth token in the userinfo
     * portion of the URL when a token is present.
     *
     * <p>This preserves the remote shape for git while avoiding any changes for
     * non-Bitbucket remotes.</p>
     */
    @NonNull
    public static String buildOAuthRemoteUrl(@CheckForNull String remote, @CheckForNull StandardUsernamePasswordCredentials credentials) {
        if (credentials == null || credentials.getPassword() == null) {
            return buildOAuthRemoteUrl(remote, (String) null);
        }
        return buildOAuthRemoteUrl(remote, credentials.getPassword().getPlainText());
    }

    @NonNull
    public static String buildOAuthRemoteUrl(@CheckForNull String remote, @CheckForNull String token) {
        if (!isBitbucketCloudRemote(remote) || token == null || token.isBlank()) {
            return remote == null ? "" : remote;
        }

        try {
            URI uri = new URI(remote);
            String scheme = uri.getScheme();
            String userInfo = "x-token-auth:" + token;
            String host = uri.getHost();
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            String fragment = uri.getRawFragment();

            if (scheme == null || host == null) {
                return remote;
            }

            return new URI(scheme, userInfo, host, uri.getPort(), path, query, fragment).toString();
        } catch (URISyntaxException e) {
            return remote;
        }
    }

    /**
     * Masks the token portion of a remote URL so that logs do not expose secrets.
     */
    @NonNull
    public static String maskTokenForLogging(@CheckForNull String remote) {
        if (remote == null || remote.isBlank()) {
            return "";
        }

        String sanitized = remote;
        int atIndex = sanitized.lastIndexOf('@');
        if (atIndex > -1) {
            int colonIndex = sanitized.indexOf(':', sanitized.indexOf("//") + 2);
            if (colonIndex > -1 && colonIndex < atIndex) {
                sanitized = sanitized.substring(0, colonIndex + 1) + "***" + sanitized.substring(atIndex);
            }
        }
        return sanitized;
    }

    /**
     * Returns the provider name used in user-facing messages.
     */
    @NonNull
    public static String providerName() {
        return "Bitbucket";
    }
}
