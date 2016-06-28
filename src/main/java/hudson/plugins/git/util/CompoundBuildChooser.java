package hudson.plugins.git.util;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Messages;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Compound build chooser enables excluding specific branches/wildcards from an age/ancestor restriction. 
 * First, the provided revisions are filtered to remove any of the specified branches via the Inverse Build Chooser algorithm,
 * then the remaining revisions undergo an Ancestor check to determine the final revision set to build.<br>
 * <br>
 * Configurations are dual-sourced: job config provides the branch filters to the Inverse algorithm, while the plugin config
 * provides the ancestor and age restriction.
 * 
 * @author Zalan Meggyesi
 */
public class CompoundBuildChooser extends DefaultBuildChooser {

    private final Integer maximumAgeInDays;
    private final String ancestorCommitSha1;
    
    @DataBoundConstructor
    public CompoundBuildChooser(Integer maximumAgeInDays, String ancestorCommitSha1) {
        this.maximumAgeInDays = maximumAgeInDays;
        this.ancestorCommitSha1 = ancestorCommitSha1;
    }
    
    public Integer getMaximumAgeInDays() {
        return maximumAgeInDays;
    }
    
    public String getAncestorCommitSha1() {
        return ancestorCommitSha1;
    }

    @Override
    public Collection<Revision> getCandidateRevisions(final boolean isPollCall,
    		String branchSpec,
            GitClient git,
            final TaskListener listener,
            final BuildData data,
            BuildChooserContext context)
                throws GitException, IOException, InterruptedException {
        
    	// Start with the inverse filtering
    	EnvVars env = context.getEnvironment();
        GitUtils utils = new GitUtils(listener, git);
        List<Revision> branchRevs = new ArrayList<Revision>(utils.getAllBranchRevisions());
        List<BranchSpec> specifiedBranches = gitSCM.getBranches();

        for (Iterator<Revision> i = branchRevs.iterator(); i.hasNext(); ) {
            Revision revision = i.next();
            for (Iterator<Branch> j = revision.getBranches().iterator(); j.hasNext(); ) {
                Branch branch = j.next();
                for (BranchSpec spec : specifiedBranches) {
                    if (spec.matches(branch.getName(), env) || HEAD.matches(branch.getName(), env)) {
                        j.remove();
                        break;
                    }
                }
            }
            if (revision.getBranches().isEmpty()) {
                i.remove();
            }
        }

        branchRevs = utils.filterTipBranches(branchRevs);

        if (branchRevs.isEmpty()) {
            listener.getLogger().println(Messages.BuildChooser_Inverse_EverythingExcluded());
        }

        for (Iterator<Revision> i = branchRevs.iterator(); i.hasNext(); ) {
            Revision r = i.next();
            if (data.hasBeenBuilt(r.getSha1())) {
                i.remove();
            }
        }

        final List<Revision> in = branchRevs;
        
        // filter candidates based on branch age and ancestry
        return git.withRepository(new RepositoryCallback<List<Revision>>() {
            public List<Revision> invoke(Repository repository, VirtualChannel channel) throws IOException {
                RevWalk walk = new RevWalk(repository);
                
                RevCommit ancestor = null;
                if (!Strings.isNullOrEmpty(ancestorCommitSha1)) {
                    try {
                        ancestor = walk.parseCommit(ObjectId.fromString(ancestorCommitSha1));
                    } catch (IllegalArgumentException e) {
                        throw new GitException(e);
                    }
                }
                
                final CommitAgeFilter ageFilter = new CommitAgeFilter(maximumAgeInDays);
                final AncestryFilter ancestryFilter = new AncestryFilter(walk, ancestor);
                
                final List<Revision> filteredCandidates = Lists.newArrayList();
                
                try {
                    for (Revision currentRevision : in) {
                        RevCommit currentRev = walk.parseCommit(ObjectId.fromString(currentRevision.getSha1String()));
                        
                        if (ageFilter.isEnabled() && !ageFilter.apply(currentRev)) {
                            continue;
                        }
                        
                        if (ancestryFilter.isEnabled() && !ancestryFilter.apply(currentRev)) {
                            continue;
                        }
                        
                        filteredCandidates.add(currentRevision);
                    }
                } catch (Throwable e) {
                    
                    // if a wrapped IOException was thrown, unwrap before throwing it
                    Iterator<IOException> ioeIter = Iterables.filter(Throwables.getCausalChain(e), IOException.class).iterator();
                    if (ioeIter.hasNext()) 
                        throw ioeIter.next();
                    else
                        throw Throwables.propagate(e);
                }
                
                // If we're in a build (not an SCM poll) and nothing new was found, run the last build again
                if (!isPollCall && filteredCandidates.isEmpty() && data.getLastBuiltRevision() != null) {
                	listener.getLogger().println(Messages.BuildChooser_BuildingLastRevision());
                	return Collections.singletonList(data.getLastBuiltRevision());
                }

                return filteredCandidates;
            }
        });
    }
    
    private static class CommitAgeFilter implements Predicate<RevCommit> {
        
        private DateTime oldestAllowableCommitDate = null;
        
        public CommitAgeFilter(Integer oldestAllowableAgeInDays) {
            if (oldestAllowableAgeInDays != null && oldestAllowableAgeInDays >= 0) {
                this.oldestAllowableCommitDate = new LocalDate()
                		.toDateTimeAtStartOfDay()
                		.minusDays(oldestAllowableAgeInDays);
            }
        }
        
        public boolean apply(RevCommit rev) {
            return new DateTime(rev.getCommitterIdent()
            		.getWhen())
            		.isAfter(this.oldestAllowableCommitDate);
        }
        
        public boolean isEnabled() {
            return oldestAllowableCommitDate != null;
        }
    }
    
    private static class AncestryFilter implements Predicate<RevCommit> {
        
        RevWalk revwalk;
        RevCommit ancestor;
        
        public AncestryFilter(RevWalk revwalk, RevCommit ancestor) {
            this.revwalk = revwalk;
            this.ancestor = ancestor;
        }
        
        public boolean apply(RevCommit rev) {
            try {
                return revwalk.isMergedInto(ancestor, rev);

            // wrap IOException so it can propagate
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        
        public boolean isEnabled() {
            return (revwalk != null) && (ancestor != null);
        }
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.BuildChooser_Compound();
        }
    }
    
    private static final long serialVersionUID = 1L;
    
    /* Ignore symbolic default branch ref. */
    private static final BranchSpec HEAD = new BranchSpec("*/HEAD");
}
