package hudson.plugins.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import java.util.UUID;

import org.eclipse.jgit.lib.ObjectId;

import org.jvnet.hudson.test.HudsonTestCase;

import hudson.EnvVars;

import hudson.console.ConsoleNote;

import hudson.model.TaskListener;

import hudson.plugins.git.GitAPI;

import hudson.util.AbstractTaskListener;
import hudson.util.StreamTaskListener;

/**
 * Unit tests of {@link GitAPI}.
 */
public class GitAPITest extends HudsonTestCase {
    private enum TmpDirType {
        EMPTY_GIT_DIR,
        VALID_GIT_DIR,
        VALID_COMMIT;
    }

    private final EnvVars env = new EnvVars();

    /**
     * Create a HudsonTestCase TmpDir of type TmpDirType.
     */
    private final File createTmpDir(TmpDirType dirType)
        throws IOException {
        final File tmpDir = createTmpDir();

        if (dirType == TmpDirType.EMPTY_GIT_DIR) {
            (new File(tmpDir, ".git")).mkdir();
        }

        if ((dirType == TmpDirType.VALID_GIT_DIR) || (dirType == TmpDirType.VALID_COMMIT)) {
            final EnvVars myEnv = new EnvVars();
            final BufferTaskListener myListener = new BufferTaskListener();
            final GitAPI api = new GitAPI("git", tmpDir, myListener, myEnv);
            /* initialize empty git repo */
            api.launchCommand("init", tmpDir.getAbsolutePath());

            if (dirType == TmpDirType.VALID_COMMIT) {
                /* Use random file name to increase chances of
                 * detecting file name related problems */
                final String fileName = "empty-file-" + org.apache.commons.lang.RandomStringUtils.random(64);
                (new File(tmpDir, fileName)).createNewFile();
                api.launchCommand("add", fileName);
                api.launchCommand("commit", "-m", "Added an empty file");
            }

            assertFalse("Unexpected errors on createTmpDir git API task listener", myListener.checkError());
        }

        return tmpDir;
    }

    /**
     * Test hasGitRepo() when it does not contain a git directory.
     */
    public void testHasGitRepoWithoutGitDirectory() throws IOException {
        final File emptyDir = createTmpDir();
        final BufferTaskListener myListener = new BufferTaskListener();
        final GitAPI api = new GitAPI("git", emptyDir, myListener, env);
        assertFalse("Empty directory has a Git repo", api.hasGitRepo());
        assertFalse("Unexpected errors on git API task listener", myListener.checkError());
    }

    /**
     * Test hasGitRepo() when it contains an invalid git directory.
     */
    public void testHasGitRepoWithEmptyGitDirectory() throws IOException {
        final File emptyGitDir = createTmpDir(TmpDirType.EMPTY_GIT_DIR);
        final BufferTaskListener myListener = new BufferTaskListener();
        final GitAPI api = new GitAPI("git", emptyGitDir, myListener, env);
        assertFalse("Invalid Git repo reported as valid", api.hasGitRepo());
        assertTrue("Missing expected errors on git API task listener", myListener.checkError());
    }

    /**
     * Test hasGitRepo() when it contains a valid git directory but no commits.
     */
    public void testHasGitRepoWithValidGitDirectoryWithoutCommits()
        throws IOException {
        final File validGitDirWithNoCommits = createTmpDir(TmpDirType.VALID_GIT_DIR);
        final BufferTaskListener myListener = new BufferTaskListener();
        final GitAPI api = new GitAPI("git", validGitDirWithNoCommits, myListener, env);
        assertTrue("Valid Git repo reported as invalid", api.hasGitRepo());
        assertFalse("Unexpected errors on git API task listener", myListener.checkError());
    }

    /**
     * Test hasGitRepo() when it contains a valid git directory with at least one commit.
     */
    public void testHasGitRepoWithValidGitDirectory() throws IOException {
        final File validGitDirWithNoCommits = createTmpDir(TmpDirType.VALID_COMMIT);
        final BufferTaskListener myListener = new BufferTaskListener();
        final GitAPI api = new GitAPI("git", validGitDirWithNoCommits, myListener, env);
        assertTrue("Valid Git repo reported as invalid", api.hasGitRepo());
        assertFalse("Unexpected errors on git API task listener", myListener.checkError());
    }

    /**
     * Test validateRevision() while trying to duplicate JENKINS-11547.
     */
    public void testValidateRevisionThrowsExceptionOnEmptyRepo()
        throws IOException {
        boolean thrown = false;
        final File validGitDirWithNoCommits = createTmpDir(TmpDirType.VALID_GIT_DIR);
        final BufferTaskListener myListener = new BufferTaskListener();
        final GitAPI api = new GitAPI("git", validGitDirWithNoCommits, myListener, env);

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
        assertTrue("Missing expected errors on git API task listener", myListener.checkError());
    }

    /**
     * Task listener that records task results to a byte array for
     * later review by test assertions and other test code.
     */
    private class BufferTaskListener extends AbstractTaskListener {
        final ByteArrayOutputStream outputStream;
        final PrintStream printStream;
        final PrintWriter printWriter;
        int errorCount;

        public BufferTaskListener() {
            outputStream = new ByteArrayOutputStream();
            printStream = new PrintStream(outputStream);
            printWriter = new PrintWriter(outputStream);
            errorCount = 0;
        }

        public void annotate(ConsoleNote ann) {
            errorCount++;

            try {
                ann.encodeTo(printStream);
            } catch (IOException ioe) {
                printStream.println("Unexpected IOException: " + ioe);
            }
        }

        public PrintWriter error(String msg) {
            errorCount++;
            printWriter.println(msg);

            return printWriter;
        }

        public PrintWriter error(String format, Object... args) {
            errorCount++;
            printWriter.println(String.format(format, args));

            return printWriter;
        }

        public boolean checkError() {
            return (outputStream.size() > 0) || (errorCount > 0) || printWriter.checkError() ||
            printStream.checkError();
        }

        public PrintStream getLogger() {
            return printStream;
        }

        public PrintWriter fatalError(String msg) {
            errorCount++;
            printWriter.println(msg);

            return printWriter;
        }

        public PrintWriter fatalError(String format, Object... args) {
            errorCount++;
            printWriter.println(String.format(format, args));

            return printWriter;
        }
    }
}
