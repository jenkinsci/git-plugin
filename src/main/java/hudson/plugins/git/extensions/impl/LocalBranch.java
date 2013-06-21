package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
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

    /**
     * Gets the parameter-expanded effective value in the context of the current build.
     */
    public String getParamLocalBranch(AbstractBuild<?, ?> build) {
        return GitSCM.getParameterString(getLocalBranch(), build);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Check out to specific local branch";
        }
    }
}