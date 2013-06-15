package hudson.plugins.git.extensions;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Extension point to tweak the behaviour of {@link GitSCM}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.EXTENSION
 */
public abstract class GitSCMExtension extends AbstractDescribableImpl<GitSCMExtension> {
    /**
     * Given a commit found during polling, check whether it should be disregarded.
     *
     * @param git GitClient object
     * @param commit
     *      The commit whose exclusion is being tested.
     * @param listener
     * @return
     *      true to disregard this commit and not trigger a build, regardless of what later {@link GitSCMExtension}s say.
     *      false to trigger a build from this commit, regardless of what later {@link GitSCMExtension}s say.
     *      null to allow other {@link GitSCMExtension}s to decide.
     */
    public Boolean isRevExcluded(GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException {
        return null;
    }

    /**
     * Given the workspace root directory, gets the working directory, which is where the repository will be checked out.
     *
     * @return working directory or null to let other {@link GitSCMExtension} control it.
     */
    public FilePath getWorkingDirectory(AbstractProject<?,?> context, FilePath workspace, EnvVars environment, TaskListener listener) throws IOException, InterruptedException, GitException {
        return null;
    }

    /**
     * Called when the checkout was completed and the working directory is filled with files.
     *
     * See {@link SCM#checkout(AbstractBuild, Launcher, FilePath, BuildListener, File)} for the available parameters,
     * except {@code workingDirectory}
     *
     * @param git
     *
     */
    public void onCheckoutCompleted(AbstractBuild<?,?> build, Launcher launcher, GitClient git, final BuildListener listener) throws IOException, InterruptedException, GitException {
    }

    /**
     * Signals when "git-clean" runs. Primarily for running "git submodule clean"
     *
     * TODO: revisit the abstraction
     */
    public void onClean(GitClient git) throws IOException, InterruptedException, GitException {
    }

    /**
     * Contribute additional environment variables for the Git invocation.
     */
    public void populateEnvironmentVariables(Map<String,String> env) {}

    @Override
    public GitSCMExtensionDescriptor getDescriptor() {
        return (GitSCMExtensionDescriptor) super.getDescriptor();
    }
}
