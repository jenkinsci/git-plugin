package hudson.plugins.git;

import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import org.kohsuke.stapler.export.Exported;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * List of changeset that went into a particular build.
 * @author Nigel Magnay
 */
public class GitChangeSetList extends ChangeLogSet<GitChangeSet> {
    private final List<GitChangeSet> changeSets;
    private final boolean showAuthorAndCommitter;

    /*package*/ GitChangeSetList(Run build, RepositoryBrowser<?> browser, List<GitChangeSet> logs) {
        this(build, browser, logs, false);
    }

    /*package*/ GitChangeSetList(Run build, RepositoryBrowser<?> browser, List<GitChangeSet> logs, boolean showAuthorAndCommitter) {
        super(build, browser);
        Collections.reverse(logs);  // put new things first
        this.changeSets = Collections.unmodifiableList(logs);
        this.showAuthorAndCommitter = showAuthorAndCommitter;
        for (GitChangeSet log : logs)
            log.setParent(this);
    }

    public boolean isEmptySet() {
        return changeSets.isEmpty();
    }

    public Iterator<GitChangeSet> iterator() {
        return changeSets.iterator();
    }

    public List<GitChangeSet> getLogs() {
        return changeSets;
    }

    @Exported
    public String getKind() {
        return "git";
    }

    /**
     * Returns true when {@link hudson.plugins.git.extensions.impl.ShowAuthorAndCommitterInChangelog}
     * was enabled on the {@link GitSCM} that produced this changelog.
     *
     * @return true if both author and committer should be displayed
     * @since TODO
     */
    public boolean isShowAuthorAndCommitter() {
        return showAuthorAndCommitter;
    }
}
