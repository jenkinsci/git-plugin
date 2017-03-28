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

    /**
     * The name of the SCM as given by the user.
     */
    public String scmName;

    /*package*/ GitChangeSetList(Run build, RepositoryBrowser<?> browser, List<GitChangeSet> logs) {
        this(build, browser, logs, null);
    }

    /*package*/ GitChangeSetList(Run build, RepositoryBrowser<?> browser, List<GitChangeSet> logs, String scmName) {
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

    @Exported
    public String getKind() {
        return "git";
    }

    public void setScmName(String scmName)
    {
        this.scmName = scmName;
    }

    @Exported
    public String getScmName()
    {
        if (scmName == null)
            scmName = "";
        return scmName;
    }
}
