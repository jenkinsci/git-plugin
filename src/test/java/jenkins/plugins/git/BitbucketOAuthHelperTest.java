package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BitbucketOAuthHelperTest {

    @Test
    void shouldRecognizeBitbucketCloudRemotes() {
        assertThat(BitbucketOAuthHelper.isBitbucketCloudRemote("https://bitbucket.org/team/repo.git"), is(true));
        assertThat(BitbucketOAuthHelper.isBitbucketCloudRemote("ssh://git@bitbucket.org/team/repo.git"), is(true));
        assertThat(BitbucketOAuthHelper.isBitbucketCloudRemote("git@bitbucket.org:team/repo.git"), is(true));
        assertThat(BitbucketOAuthHelper.isBitbucketCloudRemote("https://github.com/team/repo.git"), is(false));
        assertThat(BitbucketOAuthHelper.isBitbucketCloudRemote(null), is(false));
    }

    @Test
    void shouldProvideTransientOAuthCredentialForBitbucketCloud() throws Exception {
        StandardUsernameCredentials original = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "oauth", "OAuth token", "ignored", "example-token");

        StandardUsernameCredentials adapted = BitbucketOAuthHelper.credentialsFor(
                "https://bitbucket.org/team/repo.git", original);

        assertThat(adapted.getUsername(), is("x-token-auth"));
        assertThat(adapted.getId(), is("oauth"));
        assertThat(((UsernamePasswordCredentialsImpl) adapted).getPassword().getPlainText(), is("example-token"));
    }

    @Test
    void shouldLeaveNonBitbucketCredentialsUnchanged() throws Exception {
        StandardUsernameCredentials original = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "oauth", "OAuth token", "ignored", "example-token");

        assertThat(BitbucketOAuthHelper.credentialsFor("https://github.com/team/repo.git", original), is(original));
    }
}
