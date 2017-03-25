package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Option for disabling resolving the commit IDs for matrix builds before they are executed.
 *
 * @author Martin Storø Nyfløtt
 */
public class MatrixCommitIdOption extends FakeGitSCMExtension {

    @DataBoundConstructor
    public MatrixCommitIdOption() {
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Resolve commit id for each matrix run";
        }
    }
}
