package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import org.jenkinsci.plugins.gitclient.PushCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Add options to the push command.
 *
 * @author Tim Mertens
 */
public class PushOption extends FakeGitSCMExtension {

    private Integer timeout;

    @DataBoundConstructor
    public PushOption(Integer timeout) {
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
    public void decoratePushCommand(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, PushCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.timeout(timeout);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Advanced push behaviours";
        }
    }

}
