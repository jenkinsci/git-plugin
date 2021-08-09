package hudson.plugins.git.util;

import hudson.Extension;
import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.*;
import hudson.remoting.VirtualChannel;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.*;

/**
 * Git build chooser which will select all branches <strong>except</strong> for those which match the
 * configured branch specifiers.
 * <p>
 * e.g. If {@code &#x2a;&#x2a;/master} and {@code &#x2a;&#x2a;/release-&#x2a;} are configured as
 * "Branches to build" then any branches matching those patterns <strong>will not</strong> be built, unless
 * another branch points to the same revision.
 * <p>
 * This is useful, for example, when you have jobs building your {@code master} and various
 * {@code release} branches and you want a second job which builds all new feature branches &mdash;
 * i.e. branches which do not match these patterns &mdash; without redundantly building
 * {@code master} and the release branches again each time they change.
 *
 * @author Christopher Orr
 */
public class InverseBuildChooser extends BuildChooser {

    /* Ignore symbolic default branch ref. */
    private static final BranchSpec HEAD = new BranchSpec("*/HEAD");

    @DataBoundConstructor
    public InverseBuildChooser() {
    }

    @Override
    public Collection<Revision> getCandidateRevisions(boolean isPollCall,
            String singleBranch, GitClient git, TaskListener listener,
            BuildData buildData, BuildChooserContext context) throws GitException, IOException, InterruptedException {

        EnvVars env = context.getEnvironment();
        GitUtils utils = new GitUtils(listener, git);
        List<Revision> branchRevs = new ArrayList<>(utils.getAllBranchRevisions());
        List<BranchSpec> specifiedBranches = gitSCM.getBranches();

        // Iterate over all the revisions pointed to by branches in the repository
        for (Iterator<Revision> i = branchRevs.iterator(); i.hasNext(); ) {
            Revision revision = i.next();

            // Iterate over each branch for this revision
            for (Iterator<Branch> j = revision.getBranches().iterator(); j.hasNext(); ) {
                Branch branch = j.next();

                // Check whether this branch matches a branch spec from the job config
                for (BranchSpec spec : specifiedBranches) {
                    // If the branch matches, throw it away as we do *not* want to build it
                    if (spec.matches(branch.getName(), env) || HEAD.matches(branch.getName(), env)) {
                        j.remove();
                        break;
                    }
                }
            }

            // If we discarded all branches for this revision, ignore the whole revision
            if (revision.getBranches().isEmpty()) {
                i.remove();
            }
        }

        // Filter out branch revisions that aren't leaves
        branchRevs = utils.filterTipBranches(branchRevs);

        // Warn the user that they've done something crazy such as excluding all branches
        if (branchRevs.isEmpty()) {
            listener.getLogger().println(Messages.BuildChooser_Inverse_EverythingExcluded());
        }

        // Filter out branch revisions that have already been built
        for (Iterator<Revision> i = branchRevs.iterator(); i.hasNext(); ) {
            Revision r = i.next();
            if (buildData.hasBeenBuilt(r.getSha1())) {
                i.remove();
            }
        }

        // If we're in a build (not an SCM poll) and nothing new was found, run the last build again
        if (!isPollCall && branchRevs.isEmpty() && buildData.getLastBuiltRevision() != null) {
            listener.getLogger().println(Messages.BuildChooser_BuildingLastRevision());
            return Collections.singletonList(buildData.getLastBuiltRevision());
        }

        // Sort revisions by the date of commit, old to new, to ensure fairness in scheduling
        final List<Revision> in = branchRevs;
        return utils.git.withRepository((Repository repo, VirtualChannel channel) -> {
            Collections.sort(in,new CommitTimeComparator(repo));
            return in;
        });
    }

    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.BuildChooser_Inverse();
        }
    }

    private static final long serialVersionUID = 1L;

}
