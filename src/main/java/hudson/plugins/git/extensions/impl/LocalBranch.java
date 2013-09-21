package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.Util;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class LocalBranch extends FakeGitSCMExtension {
    private String localBranch;

    @DataBoundConstructor
    public LocalBranch(String localBranch) {
        this.localBranch = Util.fixEmptyAndTrim(localBranch);
    }

    public String getLocalBranch() {
        return localBranch;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Check out to specific local branch";
        }
    }
}