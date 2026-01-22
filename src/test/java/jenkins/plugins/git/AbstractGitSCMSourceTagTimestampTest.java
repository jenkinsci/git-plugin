package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractGitSCMSource#getTagTimestamp(RevWalk, ObjectId)}.
 * Tests the private method via reflection to ensure correct timestamp extraction.
 */
public class AbstractGitSCMSourceTagTimestampTest {

    @ClassRule
    public static GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private static String annotatedTagName;
    private static String lightweightTagName;
    private static String oldCommitSha;

    @BeforeClass
    public static void setUp() throws Exception {
        sampleRepo.init();

        // Create first commit
        sampleRepo.write("file1", "content1");
        sampleRepo.git("add", "file1");
        sampleRepo.git("commit", "-m", "First commit");
        oldCommitSha = sampleRepo.head();

        // Create lightweight tag on first commit
        lightweightTagName = "lightweight-tag";
        sampleRepo.git("tag", lightweightTagName);

        // Create second commit
        sampleRepo.write("file2", "content2");
        sampleRepo.git("add", "file2");
        sampleRepo.git("commit", "-m", "Second commit");

        // Wait to ensure different timestamps
        Thread.sleep(1500);

        // Create annotated tag on old commit (this is the key test case)
        annotatedTagName = "annotated-tag-old-commit";
        sampleRepo.git("tag", "-a", annotatedTagName, "-m", "Annotated tag on old commit", oldCommitSha);
    }

    @Test
    public void annotatedTagReturnsTagTimestamp() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            ObjectId tagId = repository.resolve(annotatedTagName);
            RevTag tag = walk.parseTag(tagId);
            long expectedTimestamp = tag.getTaggerIdent().getWhen().getTime();

            long actualTimestamp = callGetTagTimestamp(walk, tagId);

            assertEquals("Annotated tag should return tagger timestamp",
                    expectedTimestamp, actualTimestamp);
        }
    }

    @Test
    public void lightweightTagReturnsCommitTimestamp() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            ObjectId tagId = repository.resolve(lightweightTagName);
            RevCommit commit = walk.parseCommit(tagId);
            long expectedTimestamp = TimeUnit.SECONDS.toMillis(commit.getCommitTime());

            long actualTimestamp = callGetTagTimestamp(walk, tagId);

            assertEquals("Lightweight tag should return commit timestamp",
                    expectedTimestamp, actualTimestamp);
        }
    }

    @Test
    public void annotatedTagOnOldCommitReturnsTagCreationTime() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            // Get the tag timestamp
            ObjectId tagId = repository.resolve(annotatedTagName);
            long tagTimestamp = callGetTagTimestamp(walk, tagId);

            // Get the commit timestamp
            ObjectId commitId = repository.resolve(oldCommitSha);
            RevCommit commit = walk.parseCommit(commitId);
            long commitTimestamp = TimeUnit.SECONDS.toMillis(commit.getCommitTime());

            // Tag was created much later (1.5 seconds) than the commit
            assertThat("Tag timestamp should be greater than commit timestamp",
                    tagTimestamp, greaterThan(commitTimestamp));
        }
    }

    @Test
    public void timestampsAreInMilliseconds() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            ObjectId annotatedTagId = repository.resolve(annotatedTagName);
            long annotatedTimestamp = callGetTagTimestamp(walk, annotatedTagId);

            ObjectId lightweightTagId = repository.resolve(lightweightTagName);
            long lightweightTimestamp = callGetTagTimestamp(walk, lightweightTagId);

            long year2000 = 946684800000L; // Jan 1, 2000 in milliseconds
            long year3000 = 32503680000000L; // Jan 1, 3000 in milliseconds

            assertThat("Annotated tag timestamp should be in valid millisecond range",
                    annotatedTimestamp, greaterThan(year2000));
            assertThat("Annotated tag timestamp should be in valid millisecond range",
                    annotatedTimestamp, is(greaterThan(0L)));

            assertThat("Lightweight tag timestamp should be in valid millisecond range",
                    lightweightTimestamp, greaterThan(year2000));
            assertThat("Lightweight tag timestamp should be in valid millisecond range",
                    lightweightTimestamp, is(greaterThan(0L)));
        }
    }

    /**
     * Helper method to call the private getTagTimestamp method via reflection.
     */
    private long callGetTagTimestamp(RevWalk walk, ObjectId objectId) throws Exception {
        GitSCMSource source = new GitSCMSource("https://github.com/dummy/repo.git");
        Method method = AbstractGitSCMSource.class.getDeclaredMethod("getTagTimestamp", RevWalk.class, ObjectId.class);
        method.setAccessible(true);
        return (Long) method.invoke(source, walk, objectId);
    }
}
