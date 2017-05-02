package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

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

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.timeout(timeout);
    }

    @Override
    @Deprecated
    public void decorateCheckoutCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.timeout(timeout);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CheckoutOption that = (CheckoutOption) o;

        return timeout != null ? timeout.equals(that.timeout) : that.timeout == null;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return "CheckoutOption{" +
                "timeout=" + timeout +
                '}';
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Advanced checkout behaviours";
        }
    }

}
