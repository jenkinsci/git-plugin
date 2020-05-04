package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import java.util.Objects;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * git-clean before the checkout.
 *
 * @author David S Wang
 */
public class CleanBeforeCheckout extends GitSCMExtension {
    private boolean deleteUntrackedNestedRepositories;

    @DataBoundConstructor
    public CleanBeforeCheckout() {
    }

    public boolean isDeleteUntrackedNestedRepositories() {
        return deleteUntrackedNestedRepositories;
    }

    @DataBoundSetter
    public void setDeleteUntrackedNestedRepositories(boolean deleteUntrackedNestedRepositories) {
        this.deleteUntrackedNestedRepositories = deleteUntrackedNestedRepositories;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Cleaning workspace");
        git.clean(deleteUntrackedNestedRepositories);
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
        CleanBeforeCheckout that = (CleanBeforeCheckout) o;
        return deleteUntrackedNestedRepositories == that.deleteUntrackedNestedRepositories;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(deleteUntrackedNestedRepositories);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "CleanBeforeCheckout{" +
                "deleteUntrackedNestedRepositories=" + deleteUntrackedNestedRepositories +
                '}';
    }

    @Extension
    @Symbol("cleanBeforeCheckout")
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Clean before checkout";
        }
    }
}
