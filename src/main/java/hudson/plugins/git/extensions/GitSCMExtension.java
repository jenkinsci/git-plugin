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
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildChooser;
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
     *
     * @param scm
     * @param git GitClient object
     * @param commit
     *      The commit whose exclusion is being tested.
     * @param listener
     * @return
     *      true to disregard this commit and not trigger a build, regardless of what later {@link GitSCMExtension}s say.
     *      false to trigger a build from this commit, regardless of what later {@link GitSCMExtension}s say.
     *      null to allow other {@link GitSCMExtension}s to decide.
     */
    public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException {
        return null;
    }

    /**
     * Given the workspace root directory, gets the working directory, which is where the repository will be checked out.
     *
     * @return working directory or null to let other {@link GitSCMExtension} control it.
     */
    public FilePath getWorkingDirectory(GitSCM scm, AbstractProject<?, ?> context, FilePath workspace, EnvVars environment, TaskListener listener) throws IOException, InterruptedException, GitException {
        return null;
    }

    /**
     * Called after {@link BuildChooser} selects the revision to pick for this build, but before
     *
     * <p>
     * This allows extensions to select a derived revision (for example by merging another branch into
     * the chosen revision and returning it) or manipulate the state of the working tree (such as
     * running git-clean.)
     *
     * <h3>{@link #decorateRevisionToBuild(GitSCM, AbstractBuild, GitClient, BuildListener, Revision)} vs {@link BuildChooser}</h3>
     * <p>
     * {@link BuildChooser} and this method are similar in the sense that they both participate in the process
     * of determining what commits to build. So when a plugin wants to control the commit to be built, you have
     * a choice of these two approaches. The rule of the thumb is to ask yourself if your process takes
     * another commit as an input.
     *
     * {@link BuildChooser} is suitable when you do not take any commit as a parameter, and need to precisely
     * control what commit to build. For example the gerrit-trigger plugin looks at
     * a specific build parameter, then retrieves that commit from Gerrit and builds that.
     *
     * {@link #decorateRevisionToBuild(GitSCM, AbstractBuild, GitClient, BuildListener, Revision)} is suitable
     * when you accept arbitrary revision as an input and then create some derivative commits and then build that
     * result. The primary example is for speculative merge with another branch (people use this to answer
     * the question of "what happens if I were to integrate this feature branch back to the master branch?")
     *
     * @param rev
     *      The revision selected for this build.
     * @return
     *      The revision selected for this build. Unless you are decorating the given {@code rev}, return the value
     *      given in the {@code rev} parameter.
     */
    public Revision decorateRevisionToBuild(GitSCM scm, AbstractBuild<?,?> build, GitClient git, BuildListener listener, Revision rev) throws IOException, InterruptedException, GitException {
        return rev;
    }

    /**
     * Called when the checkout was completed and the working directory is filled with files.
     *
     * See {@link SCM#checkout(AbstractBuild, Launcher, FilePath, BuildListener, File)} for the available parameters,
     * except {@code workingDirectory}
     *
     * Do not move the HEAD to another commit, as by this point the commit to be built is already determined
     * and recorded (such as changelog.)
     */
    public void onCheckoutCompleted(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
    }

    /**
     * Signals when "git-clean" runs. Primarily for running "git submodule clean"
     *
     * TODO: revisit the abstraction
     */
    public void onClean(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
    }

    /**
     * Called when {@link GitClient} is created to decorate its behaviour.
     * This allows extensions to customize the behaviour of {@link GitClient}.
     */
    public GitClient decorate(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
        return git;
    }

    /**
     * Contribute additional environment variables for the Git invocation.
     */
    public void populateEnvironmentVariables(GitSCM scm, Map<String, String> env) {}

    @Override
    public GitSCMExtensionDescriptor getDescriptor() {
        return (GitSCMExtensionDescriptor) super.getDescriptor();
    }
}
