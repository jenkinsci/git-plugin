package hudson.plugins.git.extensions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import hudson.scm.SCMRevisionState;
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
     * @param scm GitSCM object
     * @param git GitClient object
     * @param commit
     *      The commit whose exclusion is being tested.
     * @param listener build log
     * @param buildData build data to be used
     * @return
     *      true to disregard this commit and not trigger a build, regardless of what later {@link GitSCMExtension}s say.
     *      false to trigger a build from this commit, regardless of what later {@link GitSCMExtension}s say.
     *      null to allow other {@link GitSCMExtension}s to decide.
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    @SuppressFBWarnings(value="NP_BOOLEAN_RETURN_NULL", justification="null used to indicate other extensions should decide")
    @CheckForNull
    public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException {
        return null;
    }

    /**
     * Given the workspace root directory, gets the working directory, which is where the repository will be checked out.
     *
     * @param scm GitSCM object
     * @param context job context for workspace root
     * @param workspace starting directory of workspace
     * @param environment environment variables used to eval
     * @param listener build log
     * @return working directory or null to let other {@link GitSCMExtension} control it.
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
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
     * <h3>{@link #decorateRevisionToBuild(GitSCM, Run, GitClient, TaskListener, Revision, Revision)} vs {@link BuildChooser}</h3>
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
     * {@link #decorateRevisionToBuild(GitSCM, Run, GitClient, TaskListener, Revision, Revision)} is suitable
     * when you accept arbitrary revision as an input and then create some derivative commits and then build that
     * result. The primary example is for speculative merge with another branch (people use this to answer
     * the question of "what happens if I were to integrate this feature branch back to the master branch?")
     *
     * @param scm GitSCM object
     * @param git GitClient object
     * @param build run context
     * @param listener build log
     * @param marked
     * 		The revision that started this build. (e.g. pre-merge)
     * @param rev
     *      The revision selected for this build.
     * @return
     *      The revision selected for this build. Unless you are decorating the given {@code rev}, return the value
     *      given in the {@code rev} parameter.
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?,?> build, GitClient git, TaskListener listener, Revision marked, Revision rev) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener) {
            return decorateRevisionToBuild(scm, (AbstractBuild) build, git, (BuildListener) listener, marked, rev);
        } else {
            return rev;
        }
    }
    
    @Deprecated
    public Revision decorateRevisionToBuild(GitSCM scm, AbstractBuild<?,?> build, GitClient git, BuildListener listener, Revision marked, Revision rev) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateRevisionToBuild", GitSCM.class, Run.class, GitClient.class, TaskListener.class, Revision.class, Revision.class)) {
            return decorateRevisionToBuild(scm, (Run) build, git, listener, marked, rev);
        } else {
            return rev;
        }
    }

    /**
     * Called before the checkout activity (including fetch and checkout) starts.
     * @param scm GitSCM object
     * @param build run context
     * @param git GitClient
     * @param listener build log
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public void beforeCheckout(GitSCM scm, Run<?,?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener) {
            beforeCheckout(scm, (AbstractBuild) build, git, (BuildListener) listener);
        }
    }

    @Deprecated
    public void beforeCheckout(GitSCM scm, AbstractBuild<?,?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "beforeCheckout", GitSCM.class, Run.class, GitClient.class, TaskListener.class)) {
            beforeCheckout(scm, (Run) build, git, listener);
        }
    }

    /**
     * Called when the checkout was completed and the working directory is filled with files.
     *
     * See {@link SCM#checkout(Run, Launcher, FilePath, TaskListener, File, SCMRevisionState)} for the available parameters,
     * except {@code workingDirectory}
     *
     * Do not move the HEAD to another commit, as by this point the commit to be built is already determined
     * and recorded (such as changelog.)
     * @param scm GitSCM object
     * @param build run context
     * @param git GitClient
     * @param listener build log
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener) {
            onCheckoutCompleted(scm, (AbstractBuild) build, git, (BuildListener) listener);
        }
    }

    @Deprecated
    public void onCheckoutCompleted(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "onCheckoutCompleted", GitSCM.class, Run.class, GitClient.class, TaskListener.class)) {
            onCheckoutCompleted(scm, (Run) build, git, listener);
        }
    }

    /**
     * Signals when "git-clean" runs. Primarily for running "git submodule clean"
     *
     * TODO: revisit the abstraction
     * @param scm GitSCM object
     * @param git GitClient
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public void onClean(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
    }

    /**
     * Called when {@link GitClient} is created to decorate its behaviour.
     * This allows extensions to customize the behaviour of {@link GitClient}.
     * @param scm GitSCM object
     * @param git GitClient
     * @return GitClient to decorate
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public GitClient decorate(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
        return git;
    }

    /**
     * Called before a {@link CloneCommand} is executed to allow extensions to alter its behaviour.
     * @param scm GitSCM object
     * @param build run context
     * @param git GitClient
     * @param listener build log
     * @param cmd clone command to be decorated
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CloneCommand cmd) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener) {
            decorateCloneCommand(scm, (AbstractBuild) build, git, (BuildListener) listener, cmd);
        }
    }

    @Deprecated
    public void decorateCloneCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, CloneCommand cmd) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateCloneCommand", GitSCM.class, Run.class, GitClient.class, TaskListener.class, CloneCommand.class)) {
            decorateCloneCommand(scm, (Run) build, git, listener, cmd);
        }
    }

    /**
     * Called before a {@link FetchCommand} is executed to allow extensions to alter its behaviour.
     * @param scm GitSCM object
     * @param git GitClient
     * @param listener build log
     * @param cmd fetch command to be decorated
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     * @deprecated use {@link #decorateCheckoutCommand(GitSCM, Run, GitClient, TaskListener, CheckoutCommand)}
     */
    @Deprecated
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
    }

    /**
     * Called before a {@link FetchCommand} is executed to allow extensions to alter its behaviour.
     * @param scm GitSCM object
     * @param run Run when fetch is called for Run. null during Job polling.
     * @param git GitClient
     * @param listener build log
     * @param cmd fetch command to be decorated
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public void decorateFetchCommand(GitSCM scm, @CheckForNull Run<?,?> run, GitClient git, TaskListener listener, FetchCommand cmd)
            throws IOException, InterruptedException, GitException {
        decorateFetchCommand(scm, git, listener, cmd);
    }

    /**
     * Called before a {@link MergeCommand} is executed to allow extensions to alter its behaviour.
     * @param scm GitSCM object
     * @param build run context
     * @param git GitClient
     * @param listener build log
     * @param cmd merge command to be decorated
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public void decorateMergeCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, MergeCommand cmd) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener) {
            decorateMergeCommand(scm, (AbstractBuild) build, git, (BuildListener) listener, cmd);
        }
    }

    @Deprecated
    public void decorateMergeCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, MergeCommand cmd) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateMergeCommand", GitSCM.class, Run.class, GitClient.class, TaskListener.class, MergeCommand.class)) {
            decorateMergeCommand(scm, (Run) build, git, listener, cmd);
        }
    }

    /**
     * Called before a {@link CheckoutCommand} is executed to allow extensions to alter its behaviour.
     * @param scm GitSCM object
     * @param build run context
     * @param git GitClient
     * @param listener build log
     * @param cmd checkout command to be decorated
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @throws GitException on git error
     */
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener) {
            decorateCheckoutCommand(scm, (AbstractBuild) build, git, (BuildListener) listener, cmd);
        }
    }

    @Deprecated
    public void decorateCheckoutCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        if (Util.isOverridden(GitSCMExtension.class, getClass(), "decorateCheckoutCommand", GitSCM.class, Run.class, GitClient.class, TaskListener.class, CheckoutCommand.class)) {
            decorateCheckoutCommand(scm, (Run) build, git, listener, cmd);
        }
    }

    /**
     * Contribute additional environment variables for the Git invocation.
     * @param scm GitSCM used as reference
     * @param env environment variables to be added
     */
    public void populateEnvironmentVariables(GitSCM scm, Map <String, String> env) {}

    /**
     * Let extension declare required GitClient implementation. git-plugin will then detect conflicts, and fallback to
     * globally configured default git client
     * @return git client type required for this extension
     */
    public GitClientType getRequiredClient() {
        return GitClientType.ANY;
    }

    /**
     *
     * @return <code>true</code> to disable the scheduling of another build to catch up
     * when multiple revisions are detected
     */
    public boolean enableMultipleRevisionDetection() {
        return true;
    }

    @Override
    public GitSCMExtensionDescriptor getDescriptor() {
        return (GitSCMExtensionDescriptor) super.getDescriptor();
    }
}
