package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Prune stale remote-tracking branches
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class PruneStaleBranch extends GitSCMExtension {
    @DataBoundConstructor
    public PruneStaleBranch() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Pruning obsolete local branches");
        cmd.prune();
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
        return o instanceof PruneStaleBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return PruneStaleBranch.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "PruneStaleBranch{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Prune stale remote-tracking branches";
        }
    }
}
