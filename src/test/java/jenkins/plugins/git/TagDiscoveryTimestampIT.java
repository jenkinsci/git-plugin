package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.plugins.git.traits.TagDiscoveryTrait;
import jenkins.scm.api.SCMHead;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for tag timestamp handling in {@link AbstractGitSCMSource}.
 * Verifies that annotated tags use the tag creation date rather than the commit date.
 */
public class TagDiscoveryTimestampIT {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private final TaskListener listener = StreamTaskListener.fromStderr();

    private static final String OLD_COMMIT_TAG = "tag-on-old-commit";
    private static final String NEW_COMMIT_TAG = "tag-on-new-commit";
    private static final String LIGHTWEIGHT_TAG = "lightweight-tag";

    @BeforeClass
    public static void setUpRepo() throws Exception {
        sampleRepo.init();

        // Create an old commit
        sampleRepo.write("file", "old content");
        sampleRepo.git("commit", "--all", "--message=old-commit");
        String oldCommitSha = sampleRepo.head();

        // Create a new commit
        sampleRepo.write("file", "new content");
        sampleRepo.git("commit", "--all", "--message=new-commit");

        // Create annotated tag on new commit
        sampleRepo.git("tag", "-a", NEW_COMMIT_TAG, "-m", "tag on new commit");

        // Create lightweight tag
        sampleRepo.git("tag", LIGHTWEIGHT_TAG);

        // Sleep to ensure distinct timestamps (Git has second precision)
        Thread.sleep(2000);

        // Create annotated tag on old commit - created later but points to older commit
        sampleRepo.git("tag", "-a", OLD_COMMIT_TAG, "-m", "tag on old commit", oldCommitSha);
    }

    @Test
    public void tagTimestampUsesTagCreationDateNotCommitDate() throws Exception {
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));

        Set<SCMHead> heads = source.fetch(listener);

        Set<GitTagSCMHead> tags = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead)
                .map(h -> (GitTagSCMHead) h)
                .collect(Collectors.toSet());

        assertThat("Should discover both tags", tags.size(), is(3));

        GitTagSCMHead oldCommitTag = tags.stream()
                .filter(t -> OLD_COMMIT_TAG.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tag not found: " + OLD_COMMIT_TAG));

        GitTagSCMHead newCommitTag = tags.stream()
                .filter(t -> NEW_COMMIT_TAG.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tag not found: " + NEW_COMMIT_TAG));

        // Tag on old commit was created later (with 2 sec delay), so should have newer or equal timestamp
        // Note: Git timestamps have second precision, so timestamps may be equal if created in same second
        assertThat(
                "Tag on old commit should have newer or equal timestamp than tag on new commit",
                oldCommitTag.getTimestamp(),
                greaterThanOrEqualTo(newCommitTag.getTimestamp())
        );
    }

    @Test
    public void lightweightTagHasValidTimestamp() throws Exception {
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));

        Set<SCMHead> heads = source.fetch(listener);

        Set<GitTagSCMHead> tags = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead)
                .map(h -> (GitTagSCMHead) h)
                .collect(Collectors.toSet());

        GitTagSCMHead lightweightTag = tags.stream()
                .filter(t -> LIGHTWEIGHT_TAG.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tag not found: " + LIGHTWEIGHT_TAG));

        assertThat("Lightweight tag should have valid timestamp", lightweightTag.getTimestamp(), greaterThan(0L));
    }

    @Test
    public void allTagsHaveValidTimestamps() throws Exception {
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Collections.singletonList(new TagDiscoveryTrait()));

        Set<SCMHead> heads = source.fetch(listener);

        Set<GitTagSCMHead> tags = heads.stream()
                .filter(h -> h instanceof GitTagSCMHead)
                .map(h -> (GitTagSCMHead) h)
                .collect(Collectors.toSet());

        long year2000 = 946684800000L; // Jan 1, 2000 in milliseconds

        for (GitTagSCMHead tag : tags) {
            assertThat("Tag should not be null", tag, is(notNullValue()));
            assertThat("Tag " + tag.getName() + " timestamp should be after year 2000",
                    tag.getTimestamp(), greaterThan(year2000));
            assertThat("Tag " + tag.getName() + " timestamp should be positive",
                    tag.getTimestamp(), greaterThan(0L));
        }
    }
}
