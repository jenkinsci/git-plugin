package hudson.plugins.git.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;
import org.jenkinsci.plugins.gitclient.GitClient;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Interface defining an API to choose which revisions ought to be
 * considered for building.
 *
 * <p>
 * This object is persisted as a part of the project configuration.
 *
 * @author magnayn
 * @author Kohsuke Kawaguchi
 */
public abstract class BuildChooser implements ExtensionPoint, Describable<BuildChooser>, Serializable {

    /**
     * Refers back to the {@link GitSCM} that owns this build chooser.
     * Do not modify from outside {@link GitSCM}.
     */
    public transient GitSCM gitSCM;

    /**
     * Short-hand to get to the display name.
     * @return display name of this build chooser
     */
    public final String getDisplayName() {
        Descriptor<?> descriptor = Jenkins.get().getDescriptor(getClass());
        return descriptor != null ? descriptor.getDisplayName() : getClass().getSimpleName();
    }

    /**
     * Get a list of revisions that are candidates to be built.
     *
     * <p>
     * This method is invoked on the node where the workspace exists, which may not be the master.
     *
     * @param isPollCall true if this method is called from pollChanges.
     * @param singleBranch contains the name of a single branch to be built
     *        this will be non-null only in the simple case, in advanced
     *        cases with multiple repositories and/or branches specified
     *        then this value will be null.
     * @param git
     *        Used for invoking Git
     * @param listener
     *        build log
     * @param buildData build data to be used
     *        Information that captures what we did during the last build.
     * @param context
     *      Object that provides access back to the model object. This is because
     *      the build chooser can be invoked on a slave where there's no direct access
     *      to the build/project for which this is invoked.
     *
     *      If {@code isPollCall} is false, then call back to both project and build are available.
     *      If {@code isPollCall} is true, then only the callback to the project is available as there's
     *      no contextual build object.
     * @return the candidate revision. Can be an empty set to indicate that there's nothing to build.
     *
     * @throws IOException on input or output error
     * @throws GitException on git error
     * @throws InterruptedException when interrupted
     */
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, @CheckForNull String singleBranch,
                    @NonNull GitClient git, @NonNull TaskListener listener, 
                    @NonNull BuildData buildData, @NonNull BuildChooserContext context) 
                    throws GitException, IOException, InterruptedException {
        // fallback to the previous signature
        return getCandidateRevisions(isPollCall, singleBranch, (IGitAPI) git, listener, buildData, context);
    }

    /**
     * @deprecated as of 1.2.0
     *     Use and override {@link #getCandidateRevisions(boolean, String, org.jenkinsci.plugins.gitclient.GitClient, hudson.model.TaskListener, BuildData, BuildChooserContext)}
     * @param isPollCall true if this method is called from pollChanges.
     * @param singleBranch contains the name of a single branch to be built
     *        this will be non-null only in the simple case, in advanced
     *        cases with multiple repositories and/or branches specified
     *        then this value will be null.
     * @param git
     *        Used for invoking Git
     * @param listener
     *        build log
     * @param buildData
     *        Information that captures what we did during the last build.
     * @param context
     *      Object that provides access back to the model object. This is because
     *      the build chooser can be invoked on a slave where there's no direct access
     *      to the build/project for which this is invoked.
     *
     *      If {@code isPollCall} is false, then call back to both project and build are available.
     *      If {@code isPollCall} is true, then only the callback to the project is available as there's
     *      no contextual build object.
     * @return
     *      the candidate revision. Can be an empty set to indicate that there's nothing to build.
     * @throws IOException on input or output error
     * @throws GitException on git error
     * @throws InterruptedException when interrupted
     */
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                               IGitAPI git, TaskListener listener, BuildData buildData, BuildChooserContext context) throws GitException, IOException, InterruptedException {
        // fallback to the previous signature
        return getCandidateRevisions(isPollCall,singleBranch,git,listener,buildData);
    }


    /**
     * @deprecated as of 1.1.17
     *      Use and override {@link #getCandidateRevisions(boolean, String, IGitAPI, TaskListener, BuildData, BuildChooserContext)}
     * @param isPollCall true if this method is called from pollChanges.
     * @param singleBranch contains the name of a single branch to be built
     *        this will be non-null only in the simple case, in advanced
     *        cases with multiple repositories and/or branches specified
     *        then this value will be null.
     * @param git GitClient used to access repository
     * @param listener build log
     * @param buildData build data to be used
     *      Information that captures what we did during the last build.
     * @return
     *      the candidate revision. Can be an empty set to indicate that there's nothing to build.
     * @throws IOException on input or output error
     * @throws GitException on git error
     */
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                               IGitAPI git, TaskListener listener, BuildData buildData) throws GitException, IOException {
        throw new UnsupportedOperationException("getCandidateRevisions method must be overridden");
    }

    /**
     * @deprecated as of 1.1.25
     *      Use and override {@link #prevBuildForChangelog(String, BuildData, IGitAPI, BuildChooserContext)}
     * @param branch contains the name of branch to be built
     *        this will be non-null only in the simple case, in advanced
     *        cases with multiple repositories and/or branches specified
     *        then this value will be null.
     * @param buildData build data to be used
     *      Information that captures what we did during the last build.
     * @param git
     *      Used for invoking Git
     * @return
     *      the candidate revision. Can be an empty set to indicate that there's nothi     */
    public Build prevBuildForChangelog(String branch, @Nullable BuildData buildData, IGitAPI git) {
        return buildData == null ? null : buildData.getLastBuildOfBranch(branch);
    }

    /**
     * Determines the baseline to compute the changelog against.
     *
     * <p>
     * {@link #getCandidateRevisions(boolean, String, IGitAPI, TaskListener, BuildData, BuildChooserContext)} determine
     * what commits can be subject for a build, and for each commit it determines the branches that contribute to them.
     *
     * <p>
     * Once {@link GitSCM} picks up a specific {@link Revision} to build, {@linkplain Revision#getBranches() for each branch},
     * in that revision, this method is called to compute the changelog.
     *
     * @param branch
     *      The branch name.
     * @param data
     *      Information that captures what we did during the last build.
     * @param git
     *      Used for invoking Git
     * @param context
     *      Object that provides access back to the model object. This is because
     *      the build chooser can be invoked on a slave where there's no direct access
     *      to the build/project for which this is invoked.
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @return candidate revision. Can be an empty set to indicate that there's nothing to build.
     */
    public Build prevBuildForChangelog(String branch, @Nullable BuildData data, GitClient git, BuildChooserContext context) throws IOException,InterruptedException {
        return prevBuildForChangelog(branch,data, (IGitAPI) git, context);
    }

    /**
     * @deprecated as of 1.2.0
     *     Use and override {@link #prevBuildForChangelog(String, BuildData, org.jenkinsci.plugins.gitclient.GitClient, BuildChooserContext)}
     * @param branch contains the name of a branch to be built
     *        this will be non-null only in the simple case, in advanced
     *        cases with multiple repositories and/or branches specified
     *        then this value will be null.
     * @param data
     *      Information that captures what we did during the last build.
     * @param git
     *      Used for invoking Git
     * @param context
     *      Object that provides access back to the model object. This is because
     *      the build chooser can be invoked on a slave where there's no direct access
     *      to the build/project for which this is invoked.
     *
     *      If {@code isPollCall} is false, then call back to both project and build are available.
     *      If {@code isPollCall} is true, then only the callback to the project is available as there's
     *      no contextual build object.
     * @return
     *      the candidate revision. Can be an empty set to indicate that there's nothing to build.
     * @throws IOException on I/O error
     * @throws GitException on git error
     * @throws InterruptedException if interrupted
     */
    public Build prevBuildForChangelog(String branch, @Nullable BuildData data, IGitAPI git, BuildChooserContext context) throws IOException,InterruptedException {
        return prevBuildForChangelog(branch,data,git);
    }

    /**
     * Returns build chooser descriptor.
     * @return build chooser descriptor
     */
    @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification="Jenkins.getInstance() is not null")
    public BuildChooserDescriptor getDescriptor() {
        return (BuildChooserDescriptor)Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * All the registered build choosers.
     * @return all registered build choosers
     */
    @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification="Jenkins.getInstance() is not null")
    public static DescriptorExtensionList<BuildChooser,BuildChooserDescriptor> all() {
        return Jenkins.get()
               .<BuildChooser,BuildChooserDescriptor>getDescriptorList(BuildChooser.class);
    }

    /**
     * All the registered build choosers that are applicable to the specified item.
     *
     * @param item the item.
     * @return All build choosers applicable to item
     */
    public static List<BuildChooserDescriptor> allApplicableTo(Item item) {
        List<BuildChooserDescriptor> result = new ArrayList<>();
        for (BuildChooserDescriptor d: all()) {
            if (d.isApplicable(item.getClass()))
                result.add(d);
        }
        return result;
    }

    private static final long serialVersionUID = 1L;

    /**
     * In a general case, a working tree is a left-over from the previous build, so it can be quite
     * messed up (such as HEAD pointing to a random branch). This method is responsible to bring the
     * working copy to a predictable clean state where candidate revisions can be evaluated.
     * <p>
     * Typical use-case is a BuildChooser which do handle pull-request merge for validation. Such a
     * BuildChooser will run the merge on working copy, and expose the merge commit as
     * {@link BuildChooser#getCandidateRevisions(boolean, String, org.jenkinsci.plugins.gitclient.GitClient, hudson.model.TaskListener, BuildData, BuildChooserContext)}
     *
     * @param git client to execute git commands on working tree
     * @param listener build log
     * @param context back-channel to master so implementation can interact with Jenkins model
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     */
    @ParametersAreNonnullByDefault
    public void prepareWorkingTree(GitClient git, TaskListener listener, BuildChooserContext context) throws IOException,InterruptedException {
        // Nop
    }
}
