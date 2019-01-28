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
    private final String scmName;

    /*package*/ GitChangeSetList(
            Run build,
            RepositoryBrowser<?> browser,
            List<GitChangeSet> logs,
            String scmName) {
        super(build, browser);
        Collections.reverse(logs);  // put new things first
        this.changeSets = Collections.unmodifiableList(logs);
        this.scmName = scmName;
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

    public String getScmName() {
        return scmName;
    }

    @Exported
    public String getKind() {
        return "git";
    }

}
