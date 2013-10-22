package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Disable Workspace-less polling via "git ls-remote". Only needed for repository that don't support ls-remote.
 *
 * @author Kohsuke Kawaguchi
 */
public class DisableRemotePoll extends FakeGitSCMExtension {
    @DataBoundConstructor
    public DisableRemotePoll() {
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Force polling using workspace";
        }
    }
}
