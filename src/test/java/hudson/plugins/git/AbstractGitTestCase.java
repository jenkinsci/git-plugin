package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.HudsonTestCase;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.transport.RemoteConfig;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Base test case for Git related stuff.
 *
 * @author Kohsuke Kawaguchi
 * @author ishaaq
 */
public abstract class AbstractGitTestCase extends HudsonTestCase {
    protected final PersonIdent johnDoe = new PersonIdent("John Doe", "john@doe.com");
    protected final PersonIdent janeDoe = new PersonIdent("Jane Doe", "jane@doe.com");

    protected TaskListener listener;

    protected Module module1;
    protected Module module2;

    /**
     * GitAPI for module1 (to simplify tests for single module)
     */
    protected GitAPI git;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        listener = new StreamTaskListener();
        module1 = new Module();
        module2 = new Module();
        git = module1.git;
    }

    protected void commit(String fileName, PersonIdent committer, String message) throws GitException {
        module1.commit(fileName, committer, message);
    }
    protected void commit(String fileName, PersonIdent author, PersonIdent committer, String message) throws GitException {
        module1.commit(fileName, author, committer, message);
    }

    class Module {
        protected File workDir;
        protected GitAPI git;
        protected FilePath workspace;
        private EnvVars envVars;

        Module() throws Exception {
            workDir = createTmpDir();
            envVars = new EnvVars();
            setAuthor(johnDoe);
            setCommitter(johnDoe);
            workspace = new FilePath(workDir);
            git = new GitAPI("git.cmd", workspace, listener, envVars);
            git.init();
        }

        protected void setAuthor(final PersonIdent author) {
            envVars.put("GIT_AUTHOR_NAME", author.getName());
            envVars.put("GIT_AUTHOR_EMAIL", author.getEmailAddress());
        }

        protected void setCommitter(final PersonIdent committer) {
            envVars.put("GIT_COMMITTER_NAME", committer.getName());
            envVars.put("GIT_COMMITTER_EMAIL", committer.getEmailAddress());
        }

        protected void commit(final String fileName, final PersonIdent committer, final String message) throws GitException {
            setAuthor(committer);
            setCommitter(committer);
            FilePath file = workspace.child(fileName);
            try {
                file.write(fileName, null);
            } catch (Exception e) {
                throw new GitException("unable to write file", e);
            }

            git.add(fileName);
            git.launchCommand("commit", "-m", message);
        }

        protected void commit(final String fileName, final PersonIdent author, final PersonIdent committer,
                            final String message) throws GitException {
            setAuthor(author);
            setCommitter(committer);
            FilePath file = workspace.child(fileName);
            try {
                file.write(fileName, null);
            } catch (Exception e) {
                throw new GitException("unable to write file", e);
            }
            git.add(fileName);
            git.launchCommand("commit", "-m", message);
        }

        protected List<Repository> createRemoteRepositories() throws IOException {
            return Arrays.asList(new Repository(workDir.getAbsolutePath(), "origin", ""));
        }

    }

}
