package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.TagDiscoveryTrait;
import jenkins.scm.api.SCMHead;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.TestJGitAPIImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for {@link AbstractGitSCMSource} and the wantTags change.
 */
@WithJenkins
@WithGitSampleRepo
class AbstractGitSCMSourceWantTagsTest {

    private JenkinsRule r;

    private static GitSampleRepoRule sampleRepo;

    private GitSCMSource source;
    private final TaskListener LISTENER = StreamTaskListener.fromStderr();

    private static final String LIGHTWEIGHT_TAG_NAME = "lightweight-tag";
    private static final String ANNOTATED_TAG_NAME = "annotated-tag";
    private static final String BRANCH_NAME = "dev";

    private static final boolean ORIGINAL_IGNORE_TAG_DISCOVERY_TRAIT = GitSCMSource.IGNORE_TAG_DISCOVERY_TRAIT;

    @BeforeAll
    static void beforeAll(GitSampleRepoRule repo) throws Exception {
        sampleRepo = repo;

        sampleRepo.init();
        sampleRepo.git("checkout", "-b", BRANCH_NAME);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=" + BRANCH_NAME + "-commit-1");
        sampleRepo.git("tag", LIGHTWEIGHT_TAG_NAME);
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=" + BRANCH_NAME + "-commit-2");
        sampleRepo.git("tag", "-a", ANNOTATED_TAG_NAME, "-m", "annotated-tag-message");
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=" + BRANCH_NAME + "-commit-3");
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;

        source = new GitSCMSource(sampleRepo.toString());
        System.setProperty(Git.class.getName() + ".mockClient", MockGitClientForTags.class.getName());
    }

    @AfterEach
    void afterEach() {
        System.clearProperty(Git.class.getName() + ".mockClient");
        GitSCMSource.IGNORE_TAG_DISCOVERY_TRAIT = ORIGINAL_IGNORE_TAG_DISCOVERY_TRAIT;
    }

    @Test
    void indexingHasNoTrait() throws Exception {
        // The source has no traits - empty result and no tags fetched
        Set<SCMHead> noHeads = source.fetch(LISTENER);
        assertThat(noHeads, is(empty()));
        assertFalse(tagsFetched);
    }

    @Test
    void indexingHasNoTraitIgnoreTagDiscoveryTrait() throws Exception {
        // The source has no traits - empty result and no tags fetched
        GitSCMSource.IGNORE_TAG_DISCOVERY_TRAIT = true;
        Set<SCMHead> noHeads = source.fetch(LISTENER);
        assertThat(noHeads, is(empty()));
        assertTrue(tagsFetched); // tags fetched but returned heads is empty
    }

    @Test
    void indexingHasTagDiscoveryTrait() throws Exception {
        // The source has the tag discovery trait - only tags fetched
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> taggedHeads = source.fetch(LISTENER);
        assertThat(
                taggedHeads.stream().map(SCMHead::getName).collect(Collectors.toList()),
                containsInAnyOrder(LIGHTWEIGHT_TAG_NAME, ANNOTATED_TAG_NAME));
        assertTrue(tagsFetched);
    }

    @Test
    void indexingHasTagDiscoveryTraitIgnoreTagDiscoveryTrait() throws Exception {
        GitSCMSource.IGNORE_TAG_DISCOVERY_TRAIT = true;
        // The source has the tag discovery trait - only tags fetched
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> taggedHeads = source.fetch(LISTENER);
        assertThat(
                taggedHeads.stream().map(SCMHead::getName).collect(Collectors.toList()),
                containsInAnyOrder(LIGHTWEIGHT_TAG_NAME, ANNOTATED_TAG_NAME));
        assertTrue(tagsFetched);
    }

    @Test
    void indexingHasBranchDiscoveryTrait() throws Exception {
        // The source has the branch discovery trait - only branches fetched
        source.setTraits(Collections.singletonList(new BranchDiscoveryTrait()));
        Set<SCMHead> branchHeads = source.fetch(LISTENER);
        assertThat(
                branchHeads.stream().map(SCMHead::getName).collect(Collectors.toList()),
                containsInAnyOrder(BRANCH_NAME, "master"));
        assertFalse(tagsFetched);
    }

    @Test
    void indexingHasBranchDiscoveryTraitIgnoreTagDiscoveryTrait() throws Exception {
        GitSCMSource.IGNORE_TAG_DISCOVERY_TRAIT = true;
        // The source has the branch discovery trait - only branches fetched
        source.setTraits(Collections.singletonList(new BranchDiscoveryTrait()));
        Set<SCMHead> branchHeads = source.fetch(LISTENER);
        assertThat(
                branchHeads.stream().map(SCMHead::getName).collect(Collectors.toList()),
                containsInAnyOrder(BRANCH_NAME, "master"));
        assertTrue(tagsFetched); // tags fetched but returned heads is only branches
    }

    @Test
    void indexingHasBranchAndTagDiscoveryTrait() throws Exception {
        // The source has the branch discovery and tag discovery trait - branches and tags fetched
        source.setTraits(List.of(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);
        assertThat(
                heads.stream().map(SCMHead::getName).collect(Collectors.toList()),
                containsInAnyOrder(BRANCH_NAME, "master", LIGHTWEIGHT_TAG_NAME, ANNOTATED_TAG_NAME));
        assertTrue(tagsFetched);
    }

    @Test
    void indexingHasBranchAndTagDiscoveryTraitIgnoreTagDiscoveryTrait() throws Exception {
        GitSCMSource.IGNORE_TAG_DISCOVERY_TRAIT = true;
        // The source has the branch discovery and tag discovery trait - branches and tags fetched
        source.setTraits(List.of(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);
        assertThat(
                heads.stream().map(SCMHead::getName).collect(Collectors.toList()),
                containsInAnyOrder(BRANCH_NAME, "master", LIGHTWEIGHT_TAG_NAME, ANNOTATED_TAG_NAME));
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
