package hudson.plugins.git.extensions.impl;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.plugins.git.ChangelogToBranchOptions;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

/**
 * This extension activates the alternative changelog computation,
 * where the changelog is calculated against a specified remote branch or any local reference
 * (e.g. branch, tag or other revision syntax which supports being prefixed by "^").
 *
 * @author <a href="mailto:dirk.reske@t-systems.com">Dirk Reske (dirk.reske@t-systems.com)</a>
 */
public class ChangelogToBranch extends GitSCMExtension {

    private final ChangelogToBranchOptions options;

    @DataBoundConstructor
    public ChangelogToBranch(ChangelogToBranchOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("options may not be null");
        }
        this.options = options;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
	// Documentation in help.html (probably copy/pasted) claimed that this extension required
	// a workspace for polling.  However, in fact it did not prior to work on JENKINS-51633.
	// Rather than always return true, maintain backward compatability since isLocalTarget() will
	// be false for all previously valid configurations.
        return options.isLocalTarget();
    }

    public ChangelogToBranchOptions getOptions() {
        return options;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Calculate changelog against a specific branch";
        }
    }
}
