package hudson.plugins.git.extensions;

import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class RevisionExclusionLogic extends GitSCMExtension implements ExtensionPoint {
    /**
     * Given a Revision, check whether it matches any exclusion rules.
     *
     * @param git GitClient object
     * @param commit
     *      The commit whose exclusion is being tested.
     * @param listener
     * @return true if any exclusion files are matched, false otherwise.
     */
    public abstract boolean isRevExcluded(GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException;

    @Override
    public RevisionExclusionLogicDescriptor getDescriptor() {
        return (RevisionExclusionLogicDescriptor) super.getDescriptor();
    }
}
