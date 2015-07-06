package hudson.plugins.git;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;

import hudson.EnvVars;
import hudson.Functions;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import org.jvnet.hudson.test.TemporaryDirectoryAllocator;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * Temporary git repository for use with tests. Tests which need a git
 * repository but do not need a running Jenkins instance should extend this
 * class so that repository setup and teardown are handled for them.
 *
 * Provides convenience methods for various repository functions.
 *
 * @author Mark Waite
 */
public abstract class AbstractGitRepository {

    protected File testGitDir;
    protected GitClient testGitClient;

    private TemporaryDirectoryAllocator tempAllocator;

    @Before
    public void createGitRepository() throws IOException, InterruptedException {
        tempAllocator = new TemporaryDirectoryAllocator();
        testGitDir = tempAllocator.allocate();
        TaskListener listener = StreamTaskListener.fromStderr();
        testGitClient = Git.with(listener, new EnvVars()).in(testGitDir).getClient();
        testGitClient.init();
    }

    @After
    public void removeGitRepository() throws IOException, InterruptedException {
        if (Functions.isWindows()) {
            System.gc(); // Reduce Windows file busy exceptions cleaning up temp dirs
        }

        tempAllocator.dispose();
    }

    /**
     * Commit fileName to this git repository
     *
     * @param fileName name of file to create
     * @throws GitException
     * @throws InterruptedException
     */
    protected void commitNewFile(final String fileName) throws GitException, InterruptedException {
        File newFile = new File(testGitDir, fileName);
        assert !newFile.exists(); // Not expected to use commitNewFile to update existing file
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(newFile, "UTF-8");
            writer.println("A file named " + fileName);
            writer.close();
            testGitClient.add(fileName);
            testGitClient.commit("Added a file named " + fileName);
        } catch (FileNotFoundException notFound) {
            throw new GitException(notFound);
        } catch (UnsupportedEncodingException unsupported) {
            throw new GitException(unsupported);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /**
     * Returns list of UserRemoteConfig for this repository.
     *
     * @return list of UserRemoteConfig for this repository
     * @throws IOException
     */
    protected List<UserRemoteConfig> remoteConfigs() throws IOException {
        List<UserRemoteConfig> list = new ArrayList<UserRemoteConfig>();
        list.add(new UserRemoteConfig(testGitDir.getAbsolutePath(), "origin", "", null));
        return list;
    }
}
