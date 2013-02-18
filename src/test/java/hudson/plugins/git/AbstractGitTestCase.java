package hudson.plugins.git;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jvnet.hudson.test.HudsonTestCase;


/**
 * Base class for single repository git plugin tests.
 *
 * @author Kohsuke Kawaguchi
 * @author ishaaq
 */
public abstract class AbstractGitTestCase extends HudsonTestCase {
	protected TaskListener listener;

	protected TestGitRepo testRepo;
	
	// aliases of testRepo properties
	protected PersonIdent johnDoe;
	protected PersonIdent janeDoe;
	protected File workDir; // aliases "gitDir"
	protected FilePath workspace; // aliases "gitDirPath"
	protected CliGitAPIImpl git;
	
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        listener = StreamTaskListener.fromStderr();

        testRepo = new TestGitRepo("unnamed", this, listener);
        johnDoe = testRepo.johnDoe;
        janeDoe = testRepo.janeDoe;
        workDir = testRepo.gitDir;
        workspace = testRepo.gitDirPath;
        git = testRepo.git;
    }

    protected void setAuthor(final PersonIdent author) {
    	testRepo.setAuthor(author);
    }

    protected void setCommitter(final PersonIdent committer) {
    	testRepo.setCommitter(committer);
    }

    protected void commit(final String fileName, final PersonIdent committer, final String message) throws GitException {
    	testRepo.commit(fileName, committer, message);
    }

    protected void commit(final String fileName, final PersonIdent author, final PersonIdent committer,
                        final String message) throws GitException {
    	testRepo.commit(fileName, author, committer, message);
    }

    protected List<UserRemoteConfig> createRemoteRepositories(String relativeTargetDir) throws IOException {
        return testRepo.remoteConfigs();
    }
}
