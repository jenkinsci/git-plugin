package hudson.plugins.git.util;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Messages;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.Collection;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Date;

public class AncestryBuildChooser extends DefaultBuildChooser {

    private final Integer maximumAgeInDays;
    private final String ancestorCommitSha1;
    
    @DataBoundConstructor
    public AncestryBuildChooser(Integer maximumAgeInDays, String ancestorCommitSha1) {
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
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String branchSpec,
                GitClient git, final TaskListener listener, BuildData data, BuildChooserContext context)
                throws GitException, IOException, InterruptedException {
        
        final Collection<Revision> candidates = super.getCandidateRevisions(isPollCall, branchSpec, git, listener, data, context);
        
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
                    for (Revision currentRevision : candidates) {
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
                
                return filteredCandidates;
            }
        });
    }
    
    private static class CommitAgeFilter implements Predicate<RevCommit> {
        
        private DateTime oldestAllowableCommitDate = null;
        
        public CommitAgeFilter(Integer oldestAllowableAgeInDays) {
            if (oldestAllowableAgeInDays != null && oldestAllowableAgeInDays >= 0) {
                this.oldestAllowableCommitDate = new LocalDate().toDateTimeAtStartOfDay().minusDays(oldestAllowableAgeInDays);
            }
        }
        
        public boolean apply(RevCommit rev) {
            if (rev == null) {
                throw new NullPointerException("RevCommit must be non-null");
            }
            return new DateTime(rev.getCommitterIdent().getWhen()).isAfter(this.oldestAllowableCommitDate);
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
        
        public boolean apply(@NonNull RevCommit rev) {
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
            return Messages.BuildChooser_Ancestry();
        }
    }
    
    private static final long serialVersionUID = 1L;
}
