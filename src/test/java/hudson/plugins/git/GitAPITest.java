package hudson.plugins.git;

import java.io.File;
import java.io.IOException;

import hudson.plugins.git.GitAPI;
import org.jvnet.hudson.test.HudsonTestCase;

import org.eclipse.jgit.lib.ObjectId;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

/**
 * Unit tests of {@link GitAPI}.
 */
public class GitAPITest extends HudsonTestCase {
    private final hudson.EnvVars env = new hudson.EnvVars();
    final TaskListener listener = StreamTaskListener.fromStderr();

    /**
     * Test hasGitRepo() when it does not contain a git directory.
     */
    public void testHasGitRepoWithoutGitDirectory() throws IOException
    {
        final File emptyDir = createTmpDir();
        final GitAPI api = new GitAPI("git", emptyDir, listener, env);
        assertFalse("Empty directory has a Git repo", api.hasGitRepo());
        emptyDir.delete();
    }

    /**
     * Test hasGitRepo() when it contains an invalid git directory.
     */
    public void testHasGitRepoWithInvalidGitDirectory() throws IOException
    {
        final File parentDir = createTmpDir();
        /* Create an empty directory named .git - "corrupt" git repo */
        final File gitDir = new File(parentDir, ".git");
        gitDir.mkdir();
        final GitAPI api = new GitAPI("git", parentDir, listener, env);
        assertFalse("Invalid Git repo reported as valid", api.hasGitRepo());
        gitDir.delete();
        parentDir.delete();
    }

    /**
     * Test hasGitRepo() when it contains a valid git directory.
     */
    public void testHasGitRepoWithValidGitDirectory() throws IOException
    {
        final File parentDir = createTmpDir();
        final GitAPI api = new GitAPI("git", parentDir, listener, env);
        /* initialize empty git repo */
        api.launchCommand("init", parentDir.getAbsolutePath());
        assertTrue("Valid Git repo reported as invalid", api.hasGitRepo());
    }

    /**
     * Test validateRevision() while trying to duplicate JENKINS-11547.
     */
    public void testValidateRevisionTbrowsExceptionOnEmptyRepo() throws IOException
    {
        boolean thrown = false;
        final File parentDir = createTmpDir();
        final GitAPI api = new GitAPI("git", parentDir, listener, env);
        /* initialize empty git repo */
        api.launchCommand("init", parentDir.getAbsolutePath());
        try {
            ObjectId id = api.validateRevision("HEAD");
            System.out.println("***** id is " + id + " *****");
        } catch (hudson.plugins.git.GitException ex) {
            /* Expected to throw an exception because the HEAD
             * revision is not yet defined in a newly created
             * repository */
            thrown = true;
        }
        assertTrue("Did not throw expected exception", thrown);
    }
}
