package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import static hudson.Util.*;

/**
 * {@link GitSCMExtension} that ignores all commits for the configured repository.
 *
 * @author Per Böhlin
 */
public class PollExclusion extends GitSCMExtension {

    @DataBoundConstructor
    public PollExclusion() {
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    @Override
    public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) {
        listener.getLogger().println("Ignored commit " + commit.getCommitId() + ": Repository excluded");
        return true;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Exclude the repository from polling all together";
        }
    }
}
