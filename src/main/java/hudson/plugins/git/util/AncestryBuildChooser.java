package hudson.plugins.git.util;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Messages;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import com.google.common.base.Throwables;

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
        return git.withRepository((Repository repository, VirtualChannel channel) -> {
            try (RevWalk walk = new RevWalk(repository)) {

                RevCommit ancestor = null;
                if (ancestorCommitSha1 != null && !ancestorCommitSha1.isEmpty()) {
                    try {
                        ancestor = walk.parseCommit(ObjectId.fromString(ancestorCommitSha1));
                    } catch (IllegalArgumentException e) {
                        throw new GitException(e);
                    }
                }

                final CommitAgeFilter ageFilter = new CommitAgeFilter(maximumAgeInDays);
                final AncestryFilter ancestryFilter = new AncestryFilter(walk, ancestor);

                final List<Revision> filteredCandidates = new ArrayList<>();

                try {
                    for (Revision currentRevision : candidates) {
                        RevCommit currentRev = walk.parseCommit(ObjectId.fromString(currentRevision.getSha1String()));

                        if (ageFilter.isEnabled() && !ageFilter.test(currentRev)) {
                            continue;
                        }

                        if (ancestryFilter.isEnabled() && !ancestryFilter.test(currentRev)) {
                            continue;
                        }

                        filteredCandidates.add(currentRevision);
                    }
                } catch (Throwable e) {

                    // if a wrapped IOException was thrown, unwrap before throwing it
                    Iterator<IOException> ioeIter = Throwables.getCausalChain(e).stream()
                            .filter(IOException.class::isInstance)
                            .map(IOException.class::cast)
                            .iterator();
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
        
        private LocalDateTime oldestAllowableCommitDate = null;
        
        public CommitAgeFilter(Integer oldestAllowableAgeInDays) {
            if (oldestAllowableAgeInDays != null && oldestAllowableAgeInDays >= 0) {
                this.oldestAllowableCommitDate = LocalDate.now().atStartOfDay().minusDays(oldestAllowableAgeInDays);
            }
        }
        
        @Override
        public boolean test(@NonNull RevCommit rev) {
            return LocalDateTime.ofInstant(rev.getCommitterIdent().getWhen().toInstant(), ZoneId.systemDefault()).isAfter(this.oldestAllowableCommitDate);
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
        
        @Override
        public boolean test(RevCommit rev) {
            try {
                return revwalk.isMergedInto(ancestor, rev);

            // wrap IOException so it can propagate
            } catch (IOException e) {
                throw new UncheckedIOException(e);
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

    @Serial
    private static final long serialVersionUID = 1L;
}
