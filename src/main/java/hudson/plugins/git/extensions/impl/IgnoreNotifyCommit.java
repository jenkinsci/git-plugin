package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Trigger from push notification should be moved to the core as a generic cross-SCM function.
 *
 * @author Kohsuke Kawaguchi
 */
public class IgnoreNotifyCommit extends FakeGitSCMExtension {
    @DataBoundConstructor
    public IgnoreNotifyCommit() {
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Don't trigger a build on commit notifications";
        }
    }
}

