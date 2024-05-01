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

    /*package*/ GitChangeSetList(Run build, RepositoryBrowser<?> browser, List<GitChangeSet> logs) {
        super(build, browser);
        Collections.reverse(logs);  // put new things first
        this.changeSets = Collections.unmodifiableList(logs);
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

}
