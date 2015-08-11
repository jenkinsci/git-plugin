package hudson.plugins.git.util;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.AbstractGitRepository;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.mockito.Mockito;

public class CandidateRevisionsTest extends AbstractGitRepository {

    private TemporaryDirectoryAllocator tempAllocator;
    private File testGitDir2;
    private GitClient testGitClient2;

    @Before
    public void createSecondGitRepository() throws IOException, InterruptedException {
        tempAllocator = new TemporaryDirectoryAllocator();
        testGitDir2 = tempAllocator.allocate();
        TaskListener listener = StreamTaskListener.fromStderr();
        testGitClient2 = Git.with(listener, new EnvVars())
                .in(testGitDir2)
                .using((new Random()).nextBoolean() ? "git" : "jgit")
                .getClient();
        testGitClient2.init();
    }

    @After
    public void removeSecondGitRepository() throws IOException, InterruptedException {
        if (isWindows()) {
            System.gc(); // Reduce Windows file busy exceptions cleaning up temp dirs
        }

        tempAllocator.dispose();
    }

    /**
     * Regression test for a bug that accidentally resulted in empty build
     * candidates.
     *
     * This test creates two repositories, one remote repository in testGitDir.
     * A second repository is created in testGitDir2.
     *
     * We create the following commit graph with 3 commits and 3 tags in
     * testGitDir:
     *
     * commit1(tag/a)---commit2(tag/b, tag/c)---commit3
     *
     * We then clone this repository into testGitDir2 using the remote tags as
     * refs with the given refspec. This is necessary to make the GitClient
     * recognize the tags via getRemoteBranches.
     *
     * Candidates should only include the commit pointed to by tag/b or tag/c if
     * the tags refspec is used and only tag/a has previously been built. If the
     * refspec were expanded to include the master branch, then the candidate
     * revisions would also include the master branch.
     */
    @Test
    public void testChooseWithMultipleTag() throws Exception {
        commitNewFile("file-1-in-repo-1");
        ObjectId commit1 = testGitClient.revParse("HEAD");
        assertEquals(commit1, testGitClient.revParse("master"));

        testGitClient.tag("tag/a", "Applied tag/a to commit 1");

        commitNewFile("file-2-in-repo-1");
        ObjectId commit2 = testGitClient.revParse("HEAD");
        assertEquals(commit2, testGitClient.revParse("master"));

        /* Two tags point to the same commit */
        testGitClient.tag("tag/b", "Applied tag/b to commit 2");
        testGitClient.tag("tag/c", "Applied tag/c to commit 2");
        assertEquals(commit2, testGitClient.revParse("tag/b"));
        assertEquals(commit2, testGitClient.revParse("tag/c"));

        /* Advance master beyond the two tags */
        commitNewFile("file-3-in-repo-1");
        ObjectId commit3 = testGitClient.revParse("HEAD");
        assertEquals(commit3, testGitClient.revParse("master"));

        /* This refspec doesn't clone master branch, don't checkout master */
        RefSpec tagsRefSpec = new RefSpec("+refs/tags/tag/*:refs/remotes/origin/tags/tag/*");
        testGitClient2.clone_()
                .refspecs(Arrays.asList(tagsRefSpec))
                .repositoryName("origin")
                .url(testGitDir.getAbsolutePath())
                .execute();

        /* Checkout either tag/b or tag/c, same results expected */
        String randomTag = (new Random()).nextBoolean() ? "tag/b" : "tag/c";
        testGitClient2.checkout().branch("my-branch").ref(randomTag).execute();
        assertEquals(commit2, testGitClient2.revParse("tag/b"));
        assertEquals(commit2, testGitClient2.revParse("tag/c"));

        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM(testGitDir.getAbsolutePath()).getBuildChooser();

        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.hasBeenBuilt(testGitClient2.revParse("tag/a"))).thenReturn(true);
        Mockito.when(buildData.hasBeenBuilt(testGitClient2.revParse("tag/b"))).thenReturn(false);
        Mockito.when(buildData.hasBeenBuilt(testGitClient2.revParse("tag/c"))).thenReturn(false);

        BuildChooserContext context = Mockito.mock(BuildChooserContext.class);
        Mockito.when(context.getEnvironment()).thenReturn(new EnvVars());

        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, "tag/*", testGitClient2, null, buildData, context);
        assertEquals(1, candidateRevisions.size());
        String name = candidateRevisions.iterator().next().getBranches().iterator().next().getName();
        assertTrue("Wrong name: '" + name + "'", name.equals("origin/tags/tag/c") || name.equals("origin/tags/tag/b"));
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
