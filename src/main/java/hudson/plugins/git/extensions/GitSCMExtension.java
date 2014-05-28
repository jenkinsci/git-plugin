package hudson.plugins.git.extensions;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;

/**
 * Extension point to tweak the behaviour of {@link GitSCM}.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.0.0
 */
public abstract class GitSCMExtension extends AbstractDescribableImpl<GitSCMExtension> {

    /**
     * @return <code>true</code> when this extension has a requirement to get a workspace during polling,
     * typically as it has to check for incoming changes, not just remote HEAD.
     */
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

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
    public FilePath getWorkingDirectory(GitSCM scm, Job<?, ?> context, FilePath workspace, EnvVars environment, TaskListener listener) throws IOException, InterruptedException, GitException {
        if (context instanceof AbstractProject) {
            return getWorkingDirectory(scm, (AbstractProject) context, workspace, environment, listener);
        }
        return null;
    }

    @Deprecated
    public FilePath getWorkingDirectory(GitSCM scm, AbstractProject<?, ?> context, FilePath workspace, EnvVars environment, TaskListener listener) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "getWorkingDirectory", GitSCM.class, Job.class, FilePath.class, EnvVars.class, TaskListener.class)) {
            return getWorkingDirectory(scm, (Job) context, workspace, environment, listener);
        }
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
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?,?> build, GitClient git, BuildListener listener, Revision rev) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild) {
            return decorateRevisionToBuild(scm, (AbstractBuild) build, git, listener, rev);
        } else {
            return rev;
        }
    }
    
    @Deprecated
    public Revision decorateRevisionToBuild(GitSCM scm, AbstractBuild<?,?> build, GitClient git, BuildListener listener, Revision rev) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateRevisionToBuild", GitSCM.class, Run.class, GitClient.class, BuildListener.class, Revision.class)) {
            return decorateRevisionToBuild(scm, (Run) build, git, listener, rev);
        } else {
            return rev;
        }
    }

    /**
     * Called before the checkout activity (including fetch and checkout) starts.
     */
    public void beforeCheckout(GitSCM scm, Run<?,?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild) {
            beforeCheckout(scm, (AbstractBuild) build, git, listener);
        }
    }

    @Deprecated
    public void beforeCheckout(GitSCM scm, AbstractBuild<?,?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "beforeCheckout", GitSCM.class, Run.class, GitClient.class, BuildListener.class)) {
            beforeCheckout(scm, (Run) build, git, listener);
        }
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
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild) {
            onCheckoutCompleted(scm, (AbstractBuild) build, git, listener);
        }
    }

    @Deprecated
    public void onCheckoutCompleted(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "onCheckoutCompleted", GitSCM.class, Run.class, GitClient.class, BuildListener.class)) {
            onCheckoutCompleted(scm, (Run) build, git, listener);
        }
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
     * Called before a {@link CloneCommand} is executed to allow extensions to alter its behaviour.
     */
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, BuildListener listener, CloneCommand cmd) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild) {
            decorateCloneCommand(scm, (AbstractBuild) build, git, listener, cmd);
        }
    }

    @Deprecated
    public void decorateCloneCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, CloneCommand cmd) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateCloneCommand", GitSCM.class, Run.class, GitClient.class, BuildListener.class, CloneCommand.class)) {
            decorateCloneCommand(scm, (Run) build, git, listener, cmd);
        }
    }

    /**
     * Called before a {@link FetchCommand} is executed to allow extensions to alter its behaviour.
     */
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
    }

    /**
     * Called before a {@link MergeCommand} is executed to allow extensions to alter its behaviour.
     */
    public void decorateMergeCommand(GitSCM scm, Run<?, ?> build, GitClient git, BuildListener listener, MergeCommand cmd) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild) {
            decorateMergeCommand(scm, (AbstractBuild) build, git, listener, cmd);
        }
    }

    @Deprecated
    public void decorateMergeCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, MergeCommand cmd) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateMergeCommand", GitSCM.class, Run.class, GitClient.class, BuildListener.class, MergeCommand.class)) {
            decorateMergeCommand(scm, (Run) build, git, listener, cmd);
        }
    }

    /**
     * Called before a {@link CheckoutCommand} is executed to allow extensions to alter its behaviour.
     */
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, BuildListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild) {
            decorateCheckoutCommand(scm, (AbstractBuild) build, git, listener, cmd);
        }
    }

    @Deprecated
    public void decorateCheckoutCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateCheckoutCommand", GitSCM.class, Run.class, GitClient.class, BuildListener.class, CheckoutCommand.class)) {
            decorateCheckoutCommand(scm, (Run) build, git, listener, cmd);
        }
    }

    /**
     * Contribute additional environment variables for the Git invocation.
     */
    public void populateEnvironmentVariables(GitSCM scm, Map <String, String> env) {}

    /**
     * Let extension declare required GitClient implementation. git-plugin will then detect conflicts, and fallback to
     * globally configured default git client
     */
    public GitClientType getRequiredClient() {
        return GitClientType.ANY;
    }

    @Override
    public GitSCMExtensionDescriptor getDescriptor() {
        return (GitSCMExtensionDescriptor) super.getDescriptor();
    }
}
