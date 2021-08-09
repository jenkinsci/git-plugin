package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;
import org.kohsuke.stapler.DataBoundConstructor;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * git-lfs-pull after the checkout.
 *
 * @author Matt Hauck
 */
public class GitLFSPull extends GitSCMExtension {
    @DataBoundConstructor
    public GitLFSPull() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Enabling Git LFS pull");
        List<RemoteConfig> repos = scm.getParamExpandedRepos(build, listener);
        // repos should never be empty, but check anyway
        if (!repos.isEmpty()) {
            // Pull LFS files from the first configured repository.
            // Same technique is used in GitSCM and CLoneOption.
            // Assumes the passed in scm represents a single repository, or if
            // multiple repositories are in use, the first repository in the
            // configuration is treated as authoritative.
            // Git plugin does not support multiple independent repositories
            // in a single job definition.
            cmd.lfsRemote(repos.get(0).getName());
        }
    }

    @Override
    public void determineSupportForJGit(GitSCM scm, @NonNull UnsupportedCommand cmd) {
        List<RemoteConfig> repos = scm.getRepositories();
        cmd.lfsRemote(repos.get(0).getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return o instanceof GitLFSPull;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return GitLFSPull.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "GitLFSPull{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Git LFS pull after checkout";
        }
    }
}
