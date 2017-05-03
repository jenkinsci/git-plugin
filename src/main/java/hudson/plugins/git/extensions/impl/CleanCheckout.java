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
 * git-clean after the checkout.
 *
 * @author Kohsuke Kawaguchi
 */
public class CleanCheckout extends GitSCMExtension {
    @DataBoundConstructor
    public CleanCheckout() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Cleaning workspace");
        git.clean();
        // TODO: revisit how to hand off to SubmoduleOption
        for (GitSCMExtension ext : scm.getExtensions()) {
            ext.onClean(scm, git);
        }
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
        return o instanceof CleanCheckout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return CleanCheckout.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "CleanCheckout{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Clean after checkout";
        }
    }
}
