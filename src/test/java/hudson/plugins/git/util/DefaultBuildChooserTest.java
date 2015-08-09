package hudson.plugins.git.util;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.git.AbstractGitRepository;
import java.util.Collection;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import static org.junit.Assert.*;
import org.junit.Test;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.mockito.Mockito;

/**
 * @author Arnout Engelen
 */
public class DefaultBuildChooserTest extends AbstractGitRepository {
    @Test
    public void testChooseGitRevisionToBuildByShaHash() throws Exception {
        testGitClient.commit("Commit 1");
        String shaHashCommit1 = testGitClient.getBranches().iterator().next().getSHA1String();
        testGitClient.commit("Commit 2");
        String shaHashCommit2 = testGitClient.getBranches().iterator().next().getSHA1String();
        assertNotSame(shaHashCommit1, shaHashCommit2);

        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, shaHashCommit1, testGitClient, null, null, null);

        assertEquals(1, candidateRevisions.size());
        assertEquals(shaHashCommit1, candidateRevisions.iterator().next().getSha1String());

        candidateRevisions = buildChooser.getCandidateRevisions(false, "aaa" + shaHashCommit1.substring(3), testGitClient, null, null, null);
        assertTrue(candidateRevisions.isEmpty());
    }

    /**
     * Regression test for a bug that accidentally resulted in empty build candidates.
     *
     * This test creates two repositories, one fake-remote repository in temporary directory "dir".
     * A second repository in temporary directory "dir2".
     * We create the following commit graph with 2 commits and 3 tags in "dir":
     * commit1(tag/a)---commit2(tag/b, tag/c)
     * We then clone this repository into "dir2" and fetch the remote tags as refs with the given refspec.
     * This is necessary to make the GitClient recognize the tags via getRemoteBranches.
     *
     * Expected output if only tag/a has previously been build:
     * The candidates should only include the commit pointed to by tag/b or tag/c.
     */
    @Test
    public void testChooseWithMultipleTag() throws Exception {
        TemporaryDirectoryAllocator temporaryDirectoryAllocator = new TemporaryDirectoryAllocator();
        File dir = temporaryDirectoryAllocator.allocate();
        File dir2 = temporaryDirectoryAllocator.allocate();
        String repo2path = dir2.getAbsolutePath() + "/repo2";

        runGitCommand(Arrays.asList("init"), dir);

        runGitCommand(Arrays.asList("commit", "--allow-empty", "-m", "commit1"), dir);
        runGitCommand(Arrays.asList("tag", "-a", "-m", "tag/a", "tag/a"), dir);
        runGitCommand(Arrays.asList("commit", "--allow-empty", "-m", "commit2"), dir);
        runGitCommand(Arrays.asList("tag", "-a", "-m", "tag/b", "tag/b"), dir);
        runGitCommand(Arrays.asList("tag", "-a", "-m", "tag/c", "tag/c"), dir);

        runGitCommand(Arrays.asList("clone", dir.getAbsolutePath(), "."), dir2);
        runGitCommand(Arrays.asList("fetch", "--tags", "origin", "+refs/tags/tag/*:refs/remotes/origin/tags/tag/*"), dir2);

        TaskListener listener = StreamTaskListener.fromStderr();
        GitClient client = Git.with(listener, new EnvVars()).in(dir2).getClient();
        client.init();
        String shaHashCommit1 = client.getBranches().iterator().next().getSHA1String();

        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM(dir.getAbsolutePath()).getBuildChooser();

        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.hasBeenBuilt(client.revParse("tag/a"))).thenReturn(true);
        Mockito.when(buildData.hasBeenBuilt(client.revParse("tag/b"))).thenReturn(false);
        Mockito.when(buildData.hasBeenBuilt(client.revParse("tag/c"))).thenReturn(false);

        BuildChooserContext context = Mockito.mock(BuildChooserContext.class);
        Mockito.when(context.getEnvironment()).thenReturn(new EnvVars());

        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, "tag/*", client, null, buildData, context);
        assertEquals(1, candidateRevisions.size());
        String name = candidateRevisions.iterator().next().getBranches().iterator().next().getName();
        assertTrue("Wrong name: '" + name + "'", name.equals("origin/tags/tag/c") || name.equals("origin/tags/tag/b"));
    }

    /**
     * RegExp patterns prefixed with : should pass through to DefaultBuildChooser.getAdvancedCandidateRevisions
     * @throws Exception
     */
    @Test
    public void testIsAdvancedSpec() throws Exception {
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        assertFalse(buildChooser.isAdvancedSpec("origin/master"));
        assertTrue(buildChooser.isAdvancedSpec("origin/master-*"));
        assertTrue(buildChooser.isAdvancedSpec("origin**"));
        // regexp use case
        assertTrue(buildChooser.isAdvancedSpec(":origin/master"));
        assertTrue(buildChooser.isAdvancedSpec(":origin/master-\\d{*}"));
    }

    private void runGitCommand(List<String> commands, File dir) throws IOException, InterruptedException {
        TaskListener listener = StreamTaskListener.fromStderr();
        ArrayList<String> gitCommand = new ArrayList<String>(commands);
        gitCommand.add(0, "git");
        int returnCode = new Launcher.LocalLauncher(listener).launch().cmds(
            gitCommand
        ).pwd(dir.getCanonicalPath()).join();
        if (returnCode != 0) {
            throw new IOException("git command did not return status 0");
        }
    }
}
