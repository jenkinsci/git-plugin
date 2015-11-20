package hudson.plugins.git.util;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
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
import com.google.common.collect.Maps;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Messages;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

public class AncestryBuildChooser extends BuildChooser {

    private BuildChooser buildChooser;
    private final Integer maximumAgeInDays;
    private final String ancestorCommitSha1;
    private final String prioritizedBranches;

    @DataBoundConstructor
    public AncestryBuildChooser(BuildChooser buildChooser, Integer maximumAgeInDays, String ancestorCommitSha1, String prioritizedBranches) {
        this.buildChooser = buildChooser;
        this.maximumAgeInDays = maximumAgeInDays;
        this.ancestorCommitSha1 = ancestorCommitSha1;
        this.prioritizedBranches = prioritizedBranches;
    }

    public BuildChooser getBuildChooser() {
        return buildChooser;
    }

    public Integer getMaximumAgeInDays() {
        return maximumAgeInDays;
    }

    public String getAncestorCommitSha1() {
        return ancestorCommitSha1;
    }

    public String getPrioritizedBranches() {
        return prioritizedBranches;
    }

    public List<String> getPrioritizedBranchesAsList() {
        return normalize(prioritizedBranches);
    }

    private List<String> normalize(String s) {
        if (StringUtils.isBlank(s)) {
            return Lists.newArrayList();
        } else {
            String lines[] = s.split("[\\r\\n]+");
            List<String> trimmedLines = Lists.newArrayListWithCapacity(lines.length);
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (!StringUtils.isBlank(trimmedLine)) {
                    trimmedLines.add(trimmedLine);
                }
            }

            return trimmedLines;
        }
    }

    @Override
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String branchSpec, GitClient git, final TaskListener listener, BuildData data, BuildChooserContext context)
            throws GitException, IOException, InterruptedException {

        // this BuildChooser's gitSCM exists, but the BuildChooser's gitSCM in the constructor doesn't
        buildChooser.gitSCM = gitSCM;

        final Collection<Revision> candidates = buildChooser.getCandidateRevisions(isPollCall, branchSpec, git, listener, data, context);

        // filter candidates based on branch age and ancestry
        final List<Revision> filteredCandidates = git.withRepository(new RepositoryCallback<List<Revision>>() {
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

        // sort candidate revisions by priority branches
        List<String> prioritizedBranches = getPrioritizedBranchesAsList();
        if (prioritizedBranches.size() > 0) {
            Collections.sort(filteredCandidates, new PrioritizedBranchesComparator(prioritizedBranches));
        }

        return filteredCandidates;
    }

    private static class PrioritizedBranchesComparator implements Comparator<Revision> {

        final List<String> prioritizedBranches;
        final Map<String, Integer> cachedRevisionPriorities;

        public PrioritizedBranchesComparator(List<String> prioritizedBranches) {
            this.prioritizedBranches = prioritizedBranches;
            this.cachedRevisionPriorities = Maps.newHashMap();
        }

        public int compare(Revision rev1, Revision rev2) {
            return getPriorityForRevision(rev1) - getPriorityForRevision(rev2);
        }

        /*
         * If the revision matches any priority branches, returns priority
         * corresponding to matching index. Else returns Integer.MAX_VALUE
         * (lowest priority).
         */
        private Integer getPriorityForRevision(Revision rev) {
            if (cachedRevisionPriorities.containsKey(rev.getSha1String())) {
                return cachedRevisionPriorities.get(rev.getSha1String());
            } else {
                Integer priority = 0;
                Collection<Branch> branches = rev.getBranches();

                for (String prioritizedBranch : prioritizedBranches) {
                    for (Branch branch : branches) {
                        if (branch.getName().contains(prioritizedBranch)) {
                            cachedRevisionPriorities.put(branch.getSHA1String(), priority);
                            return priority;
                        }
                    }
                    priority++;
                }

                // no revision branches match any prioritized branches
                cachedRevisionPriorities.put(rev.getSha1String(), Integer.MAX_VALUE);
                return Integer.MAX_VALUE;
            }
        }
    }

    private static class CommitAgeFilter implements Predicate<RevCommit> {

        private DateTime oldestAllowableCommitDate = null;

        public CommitAgeFilter(Integer oldestAllowableAgeInDays) {
            if (oldestAllowableAgeInDays != null && oldestAllowableAgeInDays >= 0) {
                this.oldestAllowableCommitDate = new LocalDate().toDateTimeAtStartOfDay().minusDays(oldestAllowableAgeInDays);
            }
        }

        public boolean apply(RevCommit rev) {
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

        public boolean apply(RevCommit rev) {
            try {
                return revwalk.isMergedInto(ancestor, rev);

            } catch (IOException e) {
                // wrap IOException so it can propagate
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

        public List<BuildChooserDescriptor> getBuildChooserDescriptors() {
            Jenkins jenkins = Jenkins.getInstance();

            return Lists.newArrayList(
                    (BuildChooserDescriptor) jenkins.getDescriptor(DefaultBuildChooser.class),
                    (BuildChooserDescriptor) jenkins.getDescriptor(InverseBuildChooser.class));
        }
    }

    private static final long serialVersionUID = 1L;
}
