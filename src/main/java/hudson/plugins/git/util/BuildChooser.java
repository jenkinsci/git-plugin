package hudson.plugins.git.util;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;
import org.jenkinsci.plugins.gitclient.GitClient;

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
     */
    public final String getDisplayName() {
        return getDescriptor().getDisplayName();
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
     *
     * @throws IOException
     * @throws GitException
     */
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                                                      GitClient git, TaskListener listener, BuildData buildData, BuildChooserContext context) throws GitException, IOException, InterruptedException {
        // fallback to the previous signature
        return getCandidateRevisions(isPollCall, singleBranch, (IGitAPI) git, listener, buildData, context);
    }

    /**
     * @deprecated as of 1.2.0
     *     Use and override {@link #getCandidateRevisions(boolean, String, org.jenkinsci.plugins.gitclient.GitClient, hudson.model.TaskListener, BuildData, BuildChooserContext)}
     */
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                               IGitAPI git, TaskListener listener, BuildData buildData, BuildChooserContext context) throws GitException, IOException, InterruptedException {
        // fallback to the previous signature
        return getCandidateRevisions(isPollCall,singleBranch,git,listener,buildData);
    }


    /**
     * @deprecated as of 1.1.17
     *      Use and override {@link #getCandidateRevisions(boolean, String, IGitAPI, TaskListener, BuildData, BuildChooserContext)}
     */
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                               IGitAPI git, TaskListener listener, BuildData buildData) throws GitException, IOException {
        throw new UnsupportedOperationException("getCandidateRevisions method must be overridden");
    }

    /**
     * @deprecated as of 1.1.25
     *      Use and override {@link #prevBuildForChangelog(String, BuildData, IGitAPI, BuildChooserContext)}
     */
    public BuiltRevision prevBuildForChangelog(String branch, @Nullable BuildData data, IGitAPI git) {
        return data==null?null:data.getLastBuildOfBranch(branch);
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
     * @param history
     *      Information that captures what we did during the previous builds.
     * @param git
     *      Used for invoking Git
     * @param context
     *      Object that provides access back to the model object. This is because
     *      the build chooser can be invoked on a slave where there's no direct access
     *      to the build/project for which this is invoked.
     */
    public BuiltRevision prevBuildForChangelog(String branch, BuildHistory history, GitClient git, BuildChooserContext context) throws IOException,InterruptedException {
        return prevBuildForChangelog(branch, history.asBuildData(branch), git, context);
    }


    /**
     * @deprecated override {@link #prevBuildForChangelog(String, BuildHistory, org.jenkinsci.plugins.gitclient.GitClient, BuildChooserContext)}
     */
    public BuiltRevision prevBuildForChangelog(String branch, @Nullable BuildData data, GitClient git, BuildChooserContext context) throws IOException,InterruptedException {
        return prevBuildForChangelog(branch,data, (IGitAPI) git, context);
    }

    /**
     *  as of 1.2.0
     *     Use and override {@link #prevBuildForChangelog(String, BuildData, org.jenkinsci.plugins.gitclient.GitClient, BuildChooserContext)}
     */
    public BuiltRevision prevBuildForChangelog(String branch, @Nullable BuildData data, IGitAPI git, BuildChooserContext context) throws IOException,InterruptedException {
        return prevBuildForChangelog(branch,data,git);
    }

    public BuildChooserDescriptor getDescriptor() {
        return (BuildChooserDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * All the registered build choosers.
     */
    public static DescriptorExtensionList<BuildChooser,BuildChooserDescriptor> all() {
        return Hudson.getInstance()
               .<BuildChooser,BuildChooserDescriptor>getDescriptorList(BuildChooser.class);
    }

    /**
     * All the registered build choosers that are applicable to the specified item.
     *
     * @param item the item.
     */
    public static List<BuildChooserDescriptor> allApplicableTo(Item item) {
        List<BuildChooserDescriptor> result = new ArrayList<BuildChooserDescriptor>();
        for (BuildChooserDescriptor d: all()) {
            if (d.isApplicable(item.getClass()))
                result.add(d);
        }
        return result;
    }

    private static final long serialVersionUID = 1L;
}
