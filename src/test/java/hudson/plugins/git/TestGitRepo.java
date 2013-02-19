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
	
	private EnvVars envVars;
	
	public final PersonIdent johnDoe = new PersonIdent("John Doe", "john@doe.com");
	public final PersonIdent janeDoe = new PersonIdent("Jane Doe", "jane@doe.com");
    
	public TestGitRepo(String name, HudsonTestCase forTest, TaskListener listener) throws IOException {
		this.name = name;
		this.listener = listener;
		
		envVars = new EnvVars();
		
		gitDir = forTest.createTmpDir();
		User john = User.get(johnDoe.getName(), true);
		UserProperty johnsMailerProperty = new Mailer.UserProperty(johnDoe.getEmailAddress());
		john.addProperty(johnsMailerProperty);
		
		User jane = User.get(janeDoe.getName(), true);
		UserProperty janesMailerProperty = new Mailer.UserProperty(janeDoe.getEmailAddress());
		jane.addProperty(janesMailerProperty);
		
		// initialize the environment
		setAuthor(johnDoe);
		setCommitter(johnDoe);
		
		// initialize the git interface.
		gitDirPath = new FilePath(gitDir);
		git = Git.with(listener, envVars).in(gitDir).getClient();

        // finally: initialize the repo
		git.init();
	}
	
	public void setAuthor(final PersonIdent author) {
    	envVars.put("GIT_AUTHOR_NAME", author.getName());
        envVars.put("GIT_AUTHOR_EMAIL", author.getEmailAddress());
    }

   public void setCommitter(final PersonIdent committer) {
        envVars.put("GIT_COMMITTER_NAME", committer.getName());
        envVars.put("GIT_COMMITTER_EMAIL", committer.getEmailAddress());
    }

    public void commit(final String fileName, final PersonIdent committer, final String message) throws GitException {
        setAuthor(committer);
        setCommitter(committer);
        FilePath file = gitDirPath.child(fileName);
        try {
            file.write(fileName, null);
        } catch (Exception e) {
            throw new GitException("unable to write file", e);
        }

        git.add(fileName);
        git.commit(message);
    }

    public void commit(final String fileName, final PersonIdent author, final PersonIdent committer,
                        final String message) throws GitException {
        setAuthor(author);
        setCommitter(committer);
        FilePath file = gitDirPath.child(fileName);
        try {
            file.write(fileName, null);
        } catch (Exception e) {
            throw new GitException("unable to write file", e);
        }
        git.add(fileName);
        git.commit(message);
    }

    public List<UserRemoteConfig> remoteConfigs() throws IOException {
        List<UserRemoteConfig> list = new ArrayList<UserRemoteConfig>();
        list.add(new UserRemoteConfig(gitDir.getAbsolutePath(), "origin", ""));
        return list;
    }
}
