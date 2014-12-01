package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Extension, specifying special behaviour for excluded commits.
 * @author Pavel Baranchikov
 */
public class ExcludedCommitsBehaviour extends GitSCMExtension {
    private final boolean hideExcludedCommits;

    @DataBoundConstructor
    public ExcludedCommitsBehaviour(boolean hideExcludedCommits) {
        this.hideExcludedCommits = hideExcludedCommits;
    }

    public boolean isHideExcludedCommits() {
        return hideExcludedCommits;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Excluded commits behaviour";
        }
    }

}
