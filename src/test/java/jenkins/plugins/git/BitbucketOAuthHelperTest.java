package jenkins.plugins.git;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

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
    void shouldBuildOAuthRemoteUrlsForBitbucketCloud() {
        String remote = "https://bitbucket.org/team/repo.git";
        String oauthRemote = BitbucketOAuthHelper.buildOAuthRemoteUrl(remote, "example-token");

        assertThat(oauthRemote, equalTo("https://x-token-auth:example-token@bitbucket.org/team/repo.git"));
    }

    @Test
    void shouldLeaveNonBitbucketRemotesUnchanged() {
        String remote = "https://github.com/team/repo.git";
        assertThat(BitbucketOAuthHelper.buildOAuthRemoteUrl(remote, "example-token"), equalTo(remote));
    }

    @Test
    void shouldLeaveNonHttpBitbucketRemotesUnchanged() {
        String sshRemote = "ssh://git@bitbucket.org/team/repo.git";
        String scpRemote = "git@bitbucket.org:team/repo.git";

        assertThat(BitbucketOAuthHelper.buildOAuthRemoteUrl(sshRemote, "example-token"), equalTo(sshRemote));
        assertThat(BitbucketOAuthHelper.buildOAuthRemoteUrl(scpRemote, "example-token"), equalTo(scpRemote));
    }

    @Test
    void shouldMaskTokensForSafeLogging() {
        String masked = BitbucketOAuthHelper.maskTokenForLogging("https://x-token-auth:example-token@bitbucket.org/team/repo.git");
        assertThat(masked, equalTo("https://x-token-auth:***@bitbucket.org/team/repo.git"));
        assertThat(masked, not(nullValue()));
    }
}
