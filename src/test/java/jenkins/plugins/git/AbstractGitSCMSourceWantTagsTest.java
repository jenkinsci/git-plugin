package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.TagDiscoveryTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.TestJGitAPIImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for {@link AbstractGitSCMSource} and the wantTags change.
 */
public class AbstractGitSCMSourceWantTagsTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Before
    public void setMockGitClient() {
        System.setProperty(Git.class.getName() + ".mockClient", MockGitClientForTags.class.getName());
    }

    @After
    public void clearMockGitClient() {
        System.clearProperty(Git.class.getName() + ".mockClient");
    }

    @Test
    public void indexingDoNotFetchTagsWithoutTagDiscoveryTrait() throws Exception {
        String lightweightTagName = "lightweight-tag";
        String annotatedTagName = "annotated-tag";
        String branchName = "dev";
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", branchName);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=" + branchName + "-commit-1");
        sampleRepo.git("tag", lightweightTagName);
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=" + branchName + "-commit-2");
        sampleRepo.git("tag", "-a", annotatedTagName, "-m", "annotated-tag-message");
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=" + branchName + "-commit-3");

        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        TaskListener listener = StreamTaskListener.fromStderr();

        // The source has no traits - empty result and no tags fetched
        Set<SCMHead> noHeads = source.fetch(listener);
        assertThat(noHeads, is(empty()));
        assertFalse(tagsFetched);

        // The source has the tag discovery trait - only tags fetched
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> taggedHeads = source.fetch(listener);
        assertThat(
                taggedHeads.stream().map(p -> p.getName()).collect(Collectors.toList()),
                containsInAnyOrder(lightweightTagName, annotatedTagName));
        assertTrue(tagsFetched);

        // The source has the branch discovery trait - only branches fetched
        source.setTraits(Collections.singletonList(new BranchDiscoveryTrait()));
        Set<SCMHead> branchHeads = source.fetch(listener);
        assertThat(
                branchHeads.stream().map(p -> p.getName()).collect(Collectors.toList()),
                containsInAnyOrder(branchName, "master"));
        assertFalse(tagsFetched);

        // The source has the branch discovery and tag discovery trait - branches and tags fetched
        List<SCMSourceTrait> traits = new ArrayList<>();
        traits.add(new BranchDiscoveryTrait());
        traits.add(new TagDiscoveryTrait());
        source.setTraits(traits);
        Set<SCMHead> heads = source.fetch(listener);
        assertThat(
                heads.stream().map(p -> p.getName()).collect(Collectors.toList()),
                containsInAnyOrder(branchName, "master", lightweightTagName, annotatedTagName));
        assertTrue(tagsFetched);
    }

    static boolean tagsFetched;

    public static class MockGitClientForTags extends TestJGitAPIImpl {

        public MockGitClientForTags(String exe, EnvVars env, File workspace, TaskListener listener) {
            super(workspace, listener);
        }

        @Override
        public FetchCommand fetch_() {
            // resetting to default behaviour, which is tags are fetched
            tagsFetched = true;
            final FetchCommand fetchCommand = super.fetch_();
            return new FetchCommand() {
                @Override
                public FetchCommand from(URIish urIish, List<RefSpec> list) {
                    fetchCommand.from(urIish, list);
                    return this;
                }

                @Override
                @Deprecated
                public FetchCommand prune() {
                    fetchCommand.prune(true);
                    return this;
                }

                @Override
                public FetchCommand prune(boolean b) {
                    fetchCommand.prune(b);
                    return this;
                }

                @Override
                public FetchCommand shallow(boolean b) {
                    fetchCommand.shallow(b);
                    return this;
                }

                @Override
                public FetchCommand timeout(Integer integer) {
                    fetchCommand.timeout(integer);
                    return this;
                }

                @Override
                public FetchCommand tags(boolean b) {
                    fetchCommand.tags(b);
                    // record the value being set for assertions later
                    tagsFetched = b;
                    return this;
                }

                @Override
                public FetchCommand depth(Integer integer) {
                    fetchCommand.depth(integer);
                    return this;
                }

                @Override
                public void execute() throws GitException, InterruptedException {
                    fetchCommand.execute();
                }
            };
        }
    }
}
