package hudson.plugins.git;

import java.util.Arrays;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

import hudson.plugins.git.GitStatus.CommitHookCause;
import hudson.scm.SCM;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommitHookCauseTest {

    @Test
    public void nonGitScmDoesNotMatch() {
        CommitHookCause target = new CommitHookCause(null, null);
        SCM wrongScm = mock(SCM.class);
        assertFalse("non-git SCMs are not accepted", target.isFor(wrongScm));
    }

    @Test
    public void similarURLMatches() throws Exception {
        CommitHookCause target = new CommitHookCause(null, new URIish("https://github.com/jenkinsci/git-plugin.git"));
        GitSCM scm = gitScm(new URIish("git://github.com/jenkinsci/git-plugin.git"));
        assertTrue("git SCMs with similar URLs are accepted", target.isFor(scm));
    }

    @Test
    public void differentURLFails() throws Exception {
        CommitHookCause target = new CommitHookCause(null, new URIish("https://github.com/jenkinsci/git-plugin.git"));
        GitSCM scm = gitScm(new URIish("git://github.com/jenkinsci/multiple-scms-plugin.git"));
        assertFalse("git SCMs with different URLs are not accepted", target.isFor(scm));
    }

    @Test
    public void shortDescriptionWithUnsetMatchedURL() throws Exception {
        final String sha1 = "sha1";
        final URIish matchedURL = null;
        CommitHookCause target = new CommitHookCause(sha1, matchedURL);
        assertThat(target.getShortDescription(), equalTo("commit notification sha1 for unknown repository"));
    }

    @Test
    public void shortDescriptionWithSetMatchedURL() throws Exception {
        final String sha1 = "sha1";
        final URIish matchedURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        CommitHookCause target = new CommitHookCause(sha1, matchedURL);
        assertThat(target.getShortDescription(), equalTo("commit notification sha1 for repository https://github.com/jenkinsci/git-plugin.git"));
    }

    private GitSCM gitScm(URIish scmUrl) {
        GitSCM scm = mock(GitSCM.class);
        RemoteConfig repo = mock(RemoteConfig.class);
        when(repo.getURIs()).thenReturn(Arrays.asList(scmUrl));
        when(scm.getRepositories()).thenReturn(Arrays.asList(repo));
        return scm;
    }
}
