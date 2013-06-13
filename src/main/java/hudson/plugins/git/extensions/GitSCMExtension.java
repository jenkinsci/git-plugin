package hudson.plugins.git.extensions;

import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;

/**
 * Extension point to tweak the behaviour of {@link GitSCM}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class GitSCMExtension extends AbstractDescribableImpl<GitSCMExtension> {
    /**
     * Given a commit found during polling, check whether it should be disregarded.
     *
     * @param git GitClient object
     * @param commit
     *      The commit whose exclusion is being tested.
     * @param listener
     * @return
     *      true to disregard this commit and not trigger a build, regardless of what later {@link GitSCMExtension}s say.
     *      false to trigger a build from this commit, regardless of what later {@link GitSCMExtension}s say.
     *      null to allow other {@link GitSCMExtension}s to decide.
     */
    public Boolean isRevExcluded(GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException {
        return null;
    }


    @Override
    public GitSCMExtensionDescriptor getDescriptor() {
        return (GitSCMExtensionDescriptor) super.getDescriptor();
    }}
