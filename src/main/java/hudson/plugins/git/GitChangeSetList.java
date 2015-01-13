package hudson.plugins.git;

import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.StreamBuildListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.RepositoryBrowser;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.output.NullOutputStream;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.export.Exported;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * List of changeset that went into a particular build.
 * @author Nigel Magnay
 */
public class GitChangeSetList extends ChangeLogSet<GitChangeSet> {
    private final List<GitChangeSet> filteredChangeSets;
    private final Set<GitChangeSet> excluded;
    private final List<GitChangeSet> allChangeSets;

    /*package*/ GitChangeSetList(Run build, RepositoryBrowser<?> browser, List<GitChangeSet> logs) {
        super(build, browser);
        Collections.reverse(logs);  // put new things first
        this.allChangeSets = Collections.unmodifiableList(logs);
        for (GitChangeSet log : logs)
            log.setParent(this);
        excluded = getExcluded(allChangeSets);
        final List<GitChangeSet> filteredChanges = Lists.newLinkedList(logs);
        filteredChanges.removeAll(excluded);
        filteredChangeSets = Collections.unmodifiableList(filteredChanges);
    }

    private List<GitChangeSet> getPublicChanges() {
        final GitSCM git = getGitSCM();
        final boolean hideExcludedChanges = git == null ? GitSCM.DEFAULT_HIDE_EXCLUDED_COMMITS_IN_CHANGESET
                : git.isHideExcludedInChangeList();
        return hideExcludedChanges ? filteredChangeSets : allChangeSets;
    }

    @Override
    public boolean isEmptySet() {
        return getPublicChanges().isEmpty();
    }

    public Iterator<GitChangeSet> iterator() {
        return getPublicChanges().iterator();
    }

    public boolean isLogsEmpty() {
        return allChangeSets.isEmpty();
    }

    public List<GitChangeSet> getLogs() {
        return allChangeSets;
    }

    @Exported
    public String getKind() {
        return "git";
    }

    public boolean isExcluded(GitChangeSet change) {
        return excluded.contains(change);
    }

    public int getExcludedCount() {
        return excluded.size();
    }

    private Set<GitChangeSet> getExcluded(Collection<GitChangeSet> allChangeSets) {
        final GitSCM git = getGitSCM();
        if (git != null) {
            final BuildListener buildListener = new StreamBuildListener(new NullOutputStream());
            try {
                final GitClient gitClient = git.createClient(buildListener, getRun()
                        .getEnvironment(buildListener), getRun(), null);
                final Set<GitChangeSet> excluded = Sets.newHashSet();
                for (GitChangeSet change : allChangeSets) {
                    final boolean commitExcluded = isExcludedImpl(git, gitClient, buildListener,
                            change);
                    if (commitExcluded) {
                        excluded.add(change);
                    }
                }
                return excluded;
            } catch (InterruptedException e) {
                throw new RuntimeException("Error creating git client", e);
            } catch (IOException e) {
                throw new RuntimeException("Error creating git client", e);
            }
        } else {
            return Collections.emptySet();
        }

    }

    private static boolean isExcludedImpl(GitSCM git, GitClient gitClient,
            BuildListener buildListener, GitChangeSet change) throws GitException, IOException,
            InterruptedException {
        Boolean excludeThisCommit = false;
        for (GitSCMExtension ext : git.getExtensions()) {
            excludeThisCommit = ext.isRevExcluded(git, gitClient, change, buildListener, null);
            if (excludeThisCommit != null)
                break;
        }
        return Boolean.TRUE.equals(excludeThisCommit);
    }

    /**
     * Returns Git SCM object, associated with this build
     * @param job build to search Git SCM for
     * @return Git SCM object or <code>null</code> if nothing found
     */
    private GitSCM getGitSCM(Run<?, ?> job) {
        final AbstractProject<?, ?> project = getProject(job);
        if (project == null) {
            return null;
        }
        final SCM scm = project.getScm();
        if (scm instanceof GitSCM) {
            return (GitSCM) scm;
        } else {
            return null;
        }
    }

    /**
     * Returns first found project in the Jenkins model hierarchy.
     * @param job job search project of
     * @return abstract project object, or <code>null</code> if nothing found.
     */
    private AbstractProject<?, ?> getProject(Run<?, ?> job) {
        if (job == null) {
            return null;
        }
        ModelObject nextParent = job.getParent();
        while (nextParent instanceof Item) {
            final Item nextItem = (Item) nextParent;
            if (nextParent instanceof AbstractProject) {
                return (AbstractProject<?, ?>) nextParent;
            }
            nextParent = nextItem.getParent();
        }
        return null;
    }

    /**
     * GitSCM is not cached, as the new SCM object is created on every job
     * configuration change.
     * @return SCM object
     */
    private GitSCM getGitSCM() {
        return getGitSCM(getRun());
    }
}
