package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.lang.reflect.Method;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests to ensure that tag timestamp extraction still returns correct commit details.
 * Verifies that both lightweight and annotated tags properly dereference to commits.
 */
public class TagCommitDetailsTest {

    @RegisterExtension
    public static GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private static String annotatedTagName;
    private static String lightweightTagName;

    @BeforeAll
    public static void setUp() throws Exception {
        sampleRepo.init();

        // Create first commit
        sampleRepo.write("file1", "content1");
        sampleRepo.git("add", "file1");
        sampleRepo.git("commit", "-m", "First commit");

        // Create lightweight tag
        lightweightTagName = "lightweight-tag";
        sampleRepo.git("tag", lightweightTagName);

        // Create second commit
        sampleRepo.write("file2", "content2");
        sampleRepo.git("add", "file2");
        sampleRepo.git("commit", "-m", "Second commit");

        // Create annotated tag
        annotatedTagName = "annotated-tag";
        sampleRepo.git("tag", "-a", annotatedTagName, "-m", "Annotated tag message");
    }

    @Test
    public void annotatedTagDereferencesToCommit() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            ObjectId annotatedTagId = repository.resolve(annotatedTagName);

            // The key test: parseCommit should dereference the tag to the commit
            RevCommit commit = walk.parseCommit(annotatedTagId);

            assertThat("Commit should not be null", commit, is(notNullValue()));
            assertThat("Commit should have a tree", commit.getTree(), is(notNullValue()));
            assertThat("Commit timestamp should be valid", commit.getCommitTime(), greaterThan(0));
        }
    }

    @Test
    public void lightweightTagDereferencesToCommit() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            ObjectId lightweightTagId = repository.resolve(lightweightTagName);

            // Lightweight tag directly points to commit
            RevCommit commit = walk.parseCommit(lightweightTagId);

            assertThat("Commit should not be null", commit, is(notNullValue()));
            assertThat("Commit should have a tree", commit.getTree(), is(notNullValue()));
            assertThat("Commit timestamp should be valid", commit.getCommitTime(), greaterThan(0));
        }
    }

    @Test
    public void getTagTimestampDoesNotPreventCommitParsing() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            ObjectId annotatedTagId = repository.resolve(annotatedTagName);

            // Call getTagTimestamp first
            long timestamp = callGetTagTimestamp(walk, annotatedTagId);
            assertThat("Should get valid timestamp", timestamp, greaterThan(0L));

            // Then parse the commit - this should still work
            RevCommit commit = walk.parseCommit(annotatedTagId);
            assertThat("Should still be able to parse commit after getTagTimestamp",
                    commit, is(notNullValue()));

            // And get the tree
            RevTree tree = commit.getTree();
            assertThat("Should still be able to get tree", tree, is(notNullValue()));
        }
    }

    @Test
    public void commitsHaveTreeAndParentInfo() throws Exception {
        try (Repository repository = new RepositoryBuilder()
                .setWorkTree(sampleRepo.getRoot()).build();
             RevWalk walk = new RevWalk(repository)) {

            // Get both tags
            ObjectId annotatedTagId = repository.resolve(annotatedTagName);
            ObjectId lightweightTagId = repository.resolve(lightweightTagName);

            // Parse both to commits
            RevCommit annotatedCommit = walk.parseCommit(annotatedTagId);
            RevCommit lightweightCommit = walk.parseCommit(lightweightTagId);

            // Verify both have tree
            assertThat("Annotated tag commit should have tree",
                    annotatedCommit.getTree(), is(notNullValue()));
            assertThat("Lightweight tag commit should have tree",
                    lightweightCommit.getTree(), is(notNullValue()));

            // Verify tree IDs are valid
            assertThat("Annotated tag commit tree ID should be valid",
                    annotatedCommit.getTree().name().length(), greaterThan(0));
            assertThat("Lightweight tag commit tree ID should be valid",
                    lightweightCommit.getTree().name().length(), greaterThan(0));
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
