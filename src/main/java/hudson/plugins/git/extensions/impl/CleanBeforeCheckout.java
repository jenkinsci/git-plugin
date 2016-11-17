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
 * git-clean before the checkout.
 *
 * @author David S Wang
 */
public class CleanBeforeCheckout extends GitSCMExtension {

    private boolean cleanSubmodule;
    @DataBoundConstructor
    public CleanBeforeCheckout(final boolean cleanSubmodule) {
        this.cleanSubmodule = cleanSubmodule;
    }

    public boolean isCleanSubmodule() {
        return cleanSubmodule;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Cleaning workspace");
        git.clean(cleanSubmodule);
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
        return o instanceof CleanBeforeCheckout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return CleanBeforeCheckout.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "CleanBeforeCheckout{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return "Clean before checkout";
        }
    }
}
