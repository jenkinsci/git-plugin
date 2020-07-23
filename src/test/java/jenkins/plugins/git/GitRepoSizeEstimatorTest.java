package jenkins.plugins.git;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

/**
 * Currently the test aims to functionally validate estimation of size of .git repo from a cached directory
 * TODO Test estimation with API extension point
 */
public class GitRepoSizeEstimatorTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    static final String GitBranchSCMHead_DEV_MASTER = "[GitBranchSCMHead{name='dev', ref='refs/heads/dev'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]";

    /*
    In the scenario of not having a cache or an implemented extension point, the estimation class should recommend
    NONE which means keep the impl as is.
     */
    @Test
    public void testSizeEstimationWithGithubAPI() {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        GitRepoSizeEstimator sizeEstimator = new GitRepoSizeEstimator(remote);
        assertThat(sizeEstimator.getGitTool(), is("git"));
    }

    @Test
    public void testSizeEstimationWithBitbucketAPIs() {
        String remote = "https://bitbucket.com/rishabhBudhouliya/git-plugin.git";
        GitRepoSizeEstimator sizeEstimator = new GitRepoSizeEstimator(remote);
        assertThat(sizeEstimator.getGitTool(), is("NONE"));
    }

    @org.jvnet.hudson.test.TestExtension
    public static class TestExtensionGithub extends GitRepoSizeEstimator.RepositorySizeAPI {

        @Override
        public boolean acceptsRemote(String remote) {
            return remote.contains("github");
        }

        @Override
        public Long getSizeOfRepository(String remote) {
            // from remote, remove .git and https://github.com
            long mockedSize = 500;
            return mockedSize;
        }
    }

    @org.jvnet.hudson.test.TestExtension
    public static class TestExtensionGitlab extends GitRepoSizeEstimator.RepositorySizeAPI {

        @Override
        public boolean acceptsRemote(String remote) {
            return remote.contains("gitlab");
        }

        @Override
        public Long getSizeOfRepository(String remote) {
            // from remote, remove .git and https://github.com
            long mockedSize = 1000;
            return mockedSize;
        }
    }


}
