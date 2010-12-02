package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.HudsonTestCase;
import org.spearce.jgit.lib.PersonIdent;

import java.io.File;

/**
 * Base test case for Git related stuff.
 *
 * @author Kohsuke Kawaguchi
 * @author ishaaq
 */
public class AbstractGitTestCase extends HudsonTestCase {
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
}
