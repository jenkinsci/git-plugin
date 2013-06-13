package hudson.plugins.git.extensions;

import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class RevisionExclusionLogic extends GitSCMExtension implements ExtensionPoint {
    /**
     * Given a Revision, check whether it matches any exclusion rules.
     *
     * @param git GitClient object
     * @param r Revision object
     * @param listener
     * @return true if any exclusion files are matched, false otherwise.
     */
    public abstract boolean isRevExcluded(GitClient git, Revision r, TaskListener listener, BuildData buildData);

    @Override
    public RevisionExclusionLogicDescriptor getDescriptor() {
        return (RevisionExclusionLogicDescriptor) super.getDescriptor();
    }
}
