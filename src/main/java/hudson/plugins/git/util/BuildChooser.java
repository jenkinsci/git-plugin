package hudson.plugins.git.util;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

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
     * May be an empty set.
     * @param isPollCall true if this method is called from pollChanges.
     * @param singleBranch contains the name of a single branch to be built
     *        this will be non-null only in the simple case, in advanced
     *        cases with multiple repositories and/or branches specified
     *        then this value will be null.
     * @return the candidate revision.
     *
     * @throws IOException
     * @throws GitException
     */
    public abstract Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                               IGitAPI git, TaskListener listener, BuildData buildData) throws GitException, IOException;

    /**
     * What was the last SHA1 that a named branch was built with?
     * @param branch
     * @return ObjectId, or NULL
     */
    //Build getLastBuiltRevisionOfBranch(String branch);

    /**
     * What was the last revision to be built?
     * @return
     */
    //public Revision getLastBuiltRevision();

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

    public Build prevBuildForChangelog(String singleBranch, BuildData data, IGitAPI git) {
        return data==null?null:data.getLastBuildOfBranch(singleBranch);
    }

    private static final long serialVersionUID = 1L;
}
