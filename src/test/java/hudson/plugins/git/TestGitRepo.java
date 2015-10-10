package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.model.UserProperty;
import hudson.model.User;
import hudson.tasks.Mailer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jvnet.hudson.test.HudsonTestCase;

public class TestGitRepo {
	protected String name; // The name of this repository.
	protected TaskListener listener;
	
	/**
     * This is where the commit commands create a Git repository.
     */
	public File gitDir; // was "workDir"
	public FilePath gitDirPath; // was "workspace"
	public GitClient git;
	
	public final PersonIdent johnDoe = new PersonIdent("John Doe", "john@doe.com");
	public final PersonIdent janeDoe = new PersonIdent("Jane Doe", "jane@doe.com");
    
	public TestGitRepo(String name, HudsonTestCase forTest, TaskListener listener)
            throws IOException, InterruptedException {
        this(name, forTest.createTmpDir(), listener);
    }

    public TestGitRepo(String name, File tmpDir, TaskListener listener) throws IOException, InterruptedException {
		this.name = name;
		this.listener = listener;
		
		EnvVars envVars = new EnvVars();
		
		gitDir = tmpDir;
		User john = User.get(johnDoe.getName(), true);
		UserProperty johnsMailerProperty = new Mailer.UserProperty(johnDoe.getEmailAddress());
		john.addProperty(johnsMailerProperty);
		
		User jane = User.get(janeDoe.getName(), true);
		UserProperty janesMailerProperty = new Mailer.UserProperty(janeDoe.getEmailAddress());
		jane.addProperty(janesMailerProperty);

		// initialize the git interface.
		gitDirPath = new FilePath(gitDir);
		git = Git.with(listener, envVars).in(gitDir).getClient();

        // finally: initialize the repo
		git.init();
	}
	
    /**
     * Creates a commit in current repo.
     * @param fileName relative path to the file to be commited with default content
     * @param committer author and committer of this commit
     * @param message commit message
     * @return SHA1 of latest commit
     * @throws GitException
     * @throws InterruptedException
     */
    public String commit(final String fileName, final PersonIdent committer, final String message)
            throws GitException, InterruptedException {
        return commit(fileName, fileName, committer, committer, message);
    }

    /**
     * Creates a commit in current repo.
     * @param fileName relative path to the file to be commited with default content
     * @param author author of the commit
     * @param committer committer of this commit
     * @param message commit message
     * @return SHA1 of latest commit
     * @throws GitException
     * @throws InterruptedException
     */
    public String commit(final String fileName, final PersonIdent author, final PersonIdent committer, final String message)
            throws GitException, InterruptedException {
        return commit(fileName, fileName, author, committer, message);
    }

    /**
     * Creates a commit in current repo.
     * @param fileName relative path to the file to be commited with the given content
     * @param fileContent content of the commit
     * @param committer author and committer of this commit
     * @param message commit message
     * @return SHA1 of latest commit
     * @throws GitException
     * @throws InterruptedException
     */
    public String commit(final String fileName, final String fileContent, final PersonIdent committer, final String message)
            throws GitException, InterruptedException {
        return commit(fileName, fileContent, committer, committer, message);
    }

    /**
     * Creates a commit in current repo.
     * @param fileName relative path to the file to be commited with the given content
     * @param fileContent content of the commit
     * @param author author of the commit
     * @param committer committer of this commit
     * @param message commit message
     * @return SHA1 of latest commit
     * @throws GitException
     * @throws InterruptedException
     */
    public String commit(final String fileName, final String fileContent, final PersonIdent author, final PersonIdent committer,
                         final String message) throws GitException, InterruptedException {
        FilePath file = gitDirPath.child(fileName);
        try {
            file.write(fileContent, null);
        } catch (Exception e) {
            throw new GitException("unable to write file", e);
        }
        git.add(fileName);
        git.setAuthor(author);
        git.setCommitter(committer);
        git.commit(message);
        return git.revParse("HEAD").getName();
    }

    public void tag(String tagName, String comment) throws GitException, InterruptedException {
        git.tag(tagName, comment);
    }

    public List<UserRemoteConfig> remoteConfigs() throws IOException {
        List<UserRemoteConfig> list = new ArrayList<UserRemoteConfig>();
        list.add(new UserRemoteConfig(gitDir.getAbsolutePath(), "origin", "", null));
        return list;
    }
}
