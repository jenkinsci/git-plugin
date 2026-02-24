package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        // Sleep to ensure annotated tag has a different timestamp than lightweight tag
        Thread.sleep(1100);
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

    @Test
    public void tagTimestampsAreValid() throws Exception {
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);

        Set<GitTagSCMHead> tags = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead)
                .map(h -> (GitTagSCMHead) h)
                .collect(Collectors.toSet());

        assertThat("Should discover both tags", tags.size(), is(2));

        long year2000 = 946684800000L; // Jan 1, 2000
        for (GitTagSCMHead tag : tags) {
            assertThat("Tag " + tag.getName() + " should have valid timestamp",
                    tag.getTimestamp(), greaterThan(year2000));
        }
    }

    @Test
    public void lightweightTagHasCommitTimestamp() throws Exception {
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);

        GitTagSCMHead lightweightTag = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead && h.getName().equals(LIGHTWEIGHT_TAG_NAME))
                .map(h -> (GitTagSCMHead) h)
                .findFirst()
                .orElse(null);

        assertNotNull(lightweightTag, "Lightweight tag should be discovered");
        assertThat("Lightweight tag should have a timestamp", lightweightTag.getTimestamp(), greaterThan(0L));
    }

    @Test
    public void annotatedTagHasValidTimestamp() throws Exception {
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);

        GitTagSCMHead annotatedTag = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead && h.getName().equals(ANNOTATED_TAG_NAME))
                .map(h -> (GitTagSCMHead) h)
                .findFirst()
                .orElse(null);

        assertNotNull(annotatedTag, "Annotated tag should be discovered");
        assertThat("Annotated tag should have a timestamp", annotatedTag.getTimestamp(), greaterThan(0L));
    }

    @Test
    public void lightweightAndAnnotatedTagsHaveDifferentCharacteristics() throws Exception {
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);

        Set<GitTagSCMHead> tags = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead)
                .map(h -> (GitTagSCMHead) h)
                .collect(Collectors.toSet());

        assertThat("Should discover both tags", tags.size(), is(2));

        // Both tags should have valid timestamps
        for (GitTagSCMHead tag : tags) {
            long timestamp = tag.getTimestamp();
            assertThat("Tag " + tag.getName() + " timestamp should be positive", timestamp, greaterThan(0L));
            // Timestamps should be in milliseconds (modern times are > 1.5 billion ms since epoch)
            assertThat("Tag " + tag.getName() + " timestamp should be in milliseconds",
                    timestamp, greaterThan(1500000000000L));
        }
    }

    @Test
    public void allDiscoveredTagsHaveValidTimestamps() throws Exception {
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);

        Set<GitTagSCMHead> tags = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead)
                .map(h -> (GitTagSCMHead) h)
                .collect(Collectors.toSet());

        assertThat("Should discover both tags", tags.size(), is(2));

        // All tags should have timestamps that are:
        // 1. Greater than year 2000 in milliseconds (946684800000)
        // 2. Less than or equal to current time
        long year2000Millis = 946684800000L;
        long currentTimeMillis = System.currentTimeMillis();

        for (GitTagSCMHead tag : tags) {
            long timestamp = tag.getTimestamp();
            assertThat("Tag " + tag.getName() + " timestamp should be after year 2000",
                    timestamp, greaterThan(year2000Millis));
            assertThat("Tag " + tag.getName() + " timestamp should not be in the future",
                    timestamp, lessThanOrEqualTo(currentTimeMillis));
        }
    }

    @Test
    public void annotatedTagHasDifferentTimestampFromLightweightTag() throws Exception {
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));
        Set<SCMHead> heads = source.fetch(LISTENER);

        GitTagSCMHead lightweightTag = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead && h.getName().equals(LIGHTWEIGHT_TAG_NAME))
                .map(h -> (GitTagSCMHead) h)
                .findFirst()
                .orElse(null);

        GitTagSCMHead annotatedTag = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead && h.getName().equals(ANNOTATED_TAG_NAME))
                .map(h -> (GitTagSCMHead) h)
                .findFirst()
                .orElse(null);

        assertNotNull(lightweightTag, "Lightweight tag should be discovered");
        assertNotNull(annotatedTag, "Annotated tag should be discovered");

        long lightweightTimestamp = lightweightTag.getTimestamp();
        long annotatedTimestamp = annotatedTag.getTimestamp();

        // Lightweight tag uses the commit's timestamp (commit-1)
        // Annotated tag uses the tagger's timestamp (when the tag was created, after a 1.1 second sleep)
        // They should have different timestamps due to the sleep in beforeAll
        assertFalse(
                lightweightTimestamp == annotatedTimestamp,
                "Annotated tag timestamp (" + annotatedTimestamp + ") should differ from " +
                "lightweight tag timestamp (" + lightweightTimestamp + ") " +
                "since annotated tag uses tagger's timestamp while lightweight uses commit timestamp"
        );

        // Annotated tag should be newer (created after the sleep)
        assertThat(
                "Annotated tag timestamp should be greater than lightweight tag timestamp",
                annotatedTimestamp, greaterThan(lightweightTimestamp)
        );
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
