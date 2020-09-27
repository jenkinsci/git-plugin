package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Messages;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import java.util.Objects;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;
import org.kohsuke.stapler.DataBoundConstructor;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Add options to the checkout command.
 *
 * @author <a href="mailto:mark.earl.waite@gmail.com">Mark Waite</a>
 */
public class CheckoutOption extends FakeGitSCMExtension {

    private Integer timeout;

    @DataBoundConstructor
    public CheckoutOption(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getTimeout() {
        return timeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.timeout(timeout);
    }

    @Override
    public void determineSupportForJGit(GitSCM scm, @NonNull UnsupportedCommand cmd) {
        cmd.timeout(timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void decorateCheckoutCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.timeout(timeout);
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

        CheckoutOption that = (CheckoutOption) o;

        return Objects.equals(timeout, that.timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "CheckoutOption{" +
                "timeout=" + timeout +
                '}';
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.advanced_checkout_behaviours();
        }
    }

}
