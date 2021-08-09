package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Force a re-clone.
 *
 * @author Kohsuke Kawaguchi
 */
public class WipeWorkspace extends GitSCMExtension {
    @DataBoundConstructor
    public WipeWorkspace() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeCheckout(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Wiping out workspace first.");
        git.getWorkTree().deleteContents();
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
        return o instanceof WipeWorkspace;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return WipeWorkspace.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "WipeWorkspace{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Wipe out repository & force clone";
        }
    }
}
