package jenkins.plugins.git;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
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
    public void testSizeEstimationWithNoGitCache() throws Exception {
        GitSCMSource instance = new GitSCMSource("https://github.com/rishabhBudhouliya/git-plugin.git");
        GitRepoSizeEstimator repoSizeEstimator = new GitRepoSizeEstimator(instance);
        String tool = repoSizeEstimator.getGitTool();

        // The class should make no recommendation since it can't find a .git cached directory
        assertThat(tool.equals("NONE"), is(true));
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

        GitRepoSizeEstimator repoSizeEstimator = new GitRepoSizeEstimator(source);
        /*
        Since the size of repository is 21.785 KiBs, the estimator should suggest "jgit" as an implementation
         */
        assertThat(repoSizeEstimator.getGitTool(), is("git"));
    }


}
