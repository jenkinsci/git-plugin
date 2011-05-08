package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.HudsonTestCase;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.transport.RemoteConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base test case for Git related stuff.
 *
 * @author Kohsuke Kawaguchi
 * @author ishaaq
 */
public abstract class AbstractGitTestCase extends HudsonTestCase {
    protected File workDir;
    protected GitAPI git;
    protected TaskListener listener;
    private EnvVars envVars;
    protected FilePath workspace;
    protected final PersonIdent johnDoe = new PersonIdent("John Doe", "john@doe.com");
    protected final PersonIdent janeDoe = new PersonIdent("Jane Doe", "jane@doe.com");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        workDir = createTmpDir();
        listener = new StreamTaskListener();
        envVars = new EnvVars();
        User u1 = User.get(johnDoe.getName(), true);
        User u2 = User.get(janeDoe.getName(), true);
        setAuthor(johnDoe);
        setCommitter(johnDoe);
        workspace = new FilePath(workDir);
        git = new GitAPI("git", workspace, listener, envVars);
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

    protected List<UserRemoteConfig> createRemoteRepositories(String relativeTargetDir) throws IOException {
        List<UserRemoteConfig> list = new ArrayList<UserRemoteConfig>();
        list.add(new UserRemoteConfig(workDir.getAbsolutePath(), "origin", ""));
        return list;
    }

}
