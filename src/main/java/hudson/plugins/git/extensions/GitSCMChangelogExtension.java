package hudson.plugins.git.extensions;

import java.io.IOException;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import org.jenkinsci.plugins.gitclient.ChangelogCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * FIXME JavaDoc
 * @author Zhenlei Huang
 */
public abstract class GitSCMChangelogExtension extends FakeGitSCMExtension {

    /**
     * Called before a {@link ChangelogCommand} is executed to allow extensions to alter its behaviour.
     * @param scm GitSCM object
     * @param build run context
     * @param git GitClient
     * @param listener build log
     * @param cmd changelog command to be decorated
     * @param revToBuild The revision selected for this build
     * @return true in case decorated, false otherwise
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public abstract boolean decorateChangelogCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, ChangelogCommand cmd, Revision revToBuild) throws IOException, InterruptedException, GitException;
}
