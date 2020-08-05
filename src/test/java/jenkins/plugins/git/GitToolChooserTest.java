package jenkins.plugins.git;

import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.tools.ToolProperty;
import hudson.util.StreamTaskListener;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;

import org.jenkinsci.plugins.gitclient.JGitTool;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

/**
 * The test aims to functionally validate "estimation of size" and "git implementation recommendation" from a
 * cached directory and from plugin extensions.
 */
public class GitToolChooserTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    static final String GitBranchSCMHead_DEV_MASTER = "[GitBranchSCMHead{name='dev', ref='refs/heads/dev'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]";

    /*
    In the event of having no cache but extension APIs in the ExtensionList, the estimator should recommend a tool
    instead of recommending no git implementation.
     */
    @Test
    public void testSizeEstimationWithNoGitCache() throws Exception {
        GitSCMSource instance = new GitSCMSource("https://github.com/rishabhBudhouliya/git-plugin.git");
        GitToolChooser repoSizeEstimator = new GitToolChooser(instance);
        String tool = repoSizeEstimator.getGitTool();

        // The class should make recommendation because of APIs implementation even though
        // it can't find a .git cached directory
        assertThat(tool, is(not("NONE")));
    }

    /*
    In the case of having a cached .git repository, the estimator class should estimate the size of the local checked
    out repository and ultimately provide a suggestion on the base of decided heuristic.
     */
    @Test
    public void testSizeEstimationWithGitCache() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "lightweight");
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=dev2");
        sampleRepo.git("tag", "-a", "annotated", "-m", "annotated");
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=dev3");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[]", source.fetch(listener).toString());
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());

        // Add a JGit tool to the Jenkins instance to let the estimator find and recommend "jgit"
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(new JGitTool(Collections.<ToolProperty<?>>emptyList()));

        GitToolChooser repoSizeEstimator = new GitToolChooser(source);
        /*
        Since the size of repository is 21.785 KiBs, the estimator should suggest "jgit" as an implementation
         */
        assertThat(repoSizeEstimator.getGitTool(), containsString("jgit"));
    }

    /*
    In the event of having an extension which returns the size of repository as 10000 KiB, the estimator should
    recommend "git" as the optimal implementation from the heuristics
     */
    @Test
    public void testSizeEstimationWithAPIForGit() {
        boolean useJGit = false;
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        GitToolChooser sizeEstimator = new GitToolChooser(remote, useJGit);
        assertThat(sizeEstimator.getGitTool(), containsString("git"));
    }

    /*
    In the event of having an extension which returns the size of repository as 500 KiB, the estimator should
    recommend "jgit" as the optimal implementation from the heuristics
     */
    @Test
    public void testSizeEstimationWithAPIForJGit() {
        boolean useJGit = true;
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(new JGitTool(Collections.<ToolProperty<?>>emptyList()));

        GitToolChooser sizeEstimator = new GitToolChooser(remote, useJGit);
        assertThat(sizeEstimator.getGitTool(), containsString("jgit"));
    }

    /*
    In the event of having an extension which is not applicable to the remote URL provided by the git plugin,
    the estimator recommends no git implementation
     */
    @Test
    public void testSizeEstimationWithBitbucketAPIs() {
        boolean useJGit = true;
        String remote = "https://bitbucket.com/rishabhBudhouliya/git-plugin.git";
        GitToolChooser sizeEstimator = new GitToolChooser(remote, useJGit);
        assertThat(sizeEstimator.getGitTool(), is("NONE"));
    }

    /*
    In the event of having an extension which is applicable to the remote URL but throws an exception due to some
    reason from the implemented git provider plugin, the estimator handles the exception by silently logging an
    "INFO" message and returns no recommendation.
     */
    @Test
    public void testSizeEstimationWithException() {
        String remote = "https://bitbucket.com/rishabhBudhouliya/git-plugin.git";
        GitToolChooser sizeEstimator = new GitToolChooser(remote, true);

        assertThat(sizeEstimator.getGitTool(), is("NONE"));
    }

    /*
    A test extension implemented to clone the behavior of a plugin extending the capability of providing the size of
    repo from a remote URL of "Github".
     */
    @TestExtension
    public static class TestExtensionGithub extends GitToolChooser.RepositorySizeAPI {

        @Override
        public boolean isApplicableTo(String remote) {
            return remote.contains("github");
        }

        @Override
        public Long getSizeOfRepository(String remote) {
            // from remote, remove .git and https://github.com
            return (long) 500;
        }
    }

    /*
    A test extension implemented to clone the behavior of a plugin extending the capability of providing the size of
    repo from a remote URL of "GitLab".
     */
    @TestExtension
    public static class TestExtensionGitlab extends GitToolChooser.RepositorySizeAPI {

        @Override
        public boolean isApplicableTo(String remote) {
            return remote.contains("gitlab");
        }

        @Override
        public Long getSizeOfRepository(String remote) {
            // from remote, remove .git and https://github.com
            return (long) 10000;
        }
    }

    /*
    A test extension implemented to clone the behavior of a plugin extending the capability of providing the size of
    repo from a remote URL of "BitBucket".
     */
    @TestExtension
    public static class TestExtensionBit extends GitToolChooser.RepositorySizeAPI {

        @Override
        public boolean isApplicableTo(String remote) {
            return remote.contains("bit");
        }

        @Override
        public Long getSizeOfRepository(String remote) throws IOException {
            throw new IOException();
        }
    }


}
