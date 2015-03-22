package jenkins.plugins.git;

import hudson.model.Result;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;

/**
 * Replacement class for legacy {@link hudson.plugins.git.util.Build} to prevent confusion with {@link hudson.model.Build}
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BuiltRevision extends Build {

    public BuiltRevision(Revision marked, Revision revision, int buildNumber, Result result) {
        super(marked, revision, buildNumber, result);
    }

    public BuiltRevision(Revision revision, int buildNumber, Result result) {
        super(revision, buildNumber, result);
    }
}
