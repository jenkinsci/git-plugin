package hudson.plugins.git;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import jenkins.plugins.git.GitSampleRepoRule;

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

    @Rule
    public GitSampleRepoRule repo = new GitSampleRepoRule();

    @Before
    public void createGitRepository() throws Exception {
        TaskListener listener = StreamTaskListener.fromStderr();
        repo.init();
        testGitDir = repo.getRoot();
        testGitClient = Git.with(listener, new EnvVars()).in(testGitDir).getClient();
    }

    /**
     * Commit fileName to this git repository
     *
     * @param fileName name of file to create
     * @throws GitException on git error
     * @throws InterruptedException when interrupted
     */
    protected void commitNewFile(final String fileName) throws GitException, InterruptedException {
        File newFile = new File(testGitDir, fileName);
        assert !newFile.exists(); // Not expected to use commitNewFile to update existing file
        try (PrintWriter writer = new PrintWriter(newFile, "UTF-8")) {
            writer.println("A file named " + fileName);
            writer.close();
            testGitClient.add(fileName);
            testGitClient.commit("Added a file named " + fileName);
        } catch (FileNotFoundException | UnsupportedEncodingException notFound) {
            throw new GitException(notFound);
        }
    }

    /**
     * Returns list of UserRemoteConfig for this repository.
     *
     * @return list of UserRemoteConfig for this repository
     * @throws IOException on input or output error
     */
    protected List<UserRemoteConfig> remoteConfigs() throws IOException {
        List<UserRemoteConfig> list = new ArrayList<>();
        list.add(new UserRemoteConfig(testGitDir.getAbsolutePath(), "origin", "", null));
        return list;
    }

    /** inline ${@link hudson.Functions#isWindows()} to prevent a transient remote classloader issue */
    private boolean isWindows() {
        return File.pathSeparatorChar==';';
    }
}
