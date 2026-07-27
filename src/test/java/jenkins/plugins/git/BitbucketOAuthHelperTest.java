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
    void shouldExchangeOAuthConsumerCredentialsForBitbucketCloud() throws Exception {
        StandardUsernameCredentials original = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                "oauth",
                "OAuth consumer",
                "123456789012345678",
                "12345678901234567890123456789012");

        StandardUsernameCredentials adapted = BitbucketOAuthHelper.credentialsFor(
                "https://bitbucket.org/team/repo.git",
                original,
                (id, key, secret) -> {
                    assertThat(id, is("oauth"));
                    assertThat(key, is("123456789012345678"));
                    assertThat(secret, is("12345678901234567890123456789012"));
                    return "access-token";
                });

        assertThat(adapted.getUsername(), is("x-token-auth"));
        assertThat(adapted.getId(), is("oauth"));
        assertThat(((UsernamePasswordCredentialsImpl) adapted).getPassword().getPlainText(), is("access-token"));
    }

    @Test
    void shouldLeaveNonOAuthCredentialsUnchanged() throws Exception {
        StandardUsernameCredentials original = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "password", "App password", "user@example.com", "example-password");

        assertThat(BitbucketOAuthHelper.credentialsFor("https://github.com/team/repo.git", original), is(original));
        assertThat(BitbucketOAuthHelper.credentialsFor("https://bitbucket.org/team/repo.git", original), is(original));
    }

    @Test
    void shouldLeaveExistingAccessTokenCredentialsUnchanged() throws Exception {
        StandardUsernameCredentials original = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "token", "Access token", "x-token-auth", "example-token");

        assertThat(BitbucketOAuthHelper.credentialsFor("https://bitbucket.org/team/repo.git", original), is(original));
    }
}
