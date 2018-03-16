package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Avoid storing unnecessary builds-by-branch data for every build.
 *
 * @author Jacob Keller
 */
public class NoBuildsByBranchHistory extends FakeGitSCMExtension {

    @DataBoundConstructor
    public NoBuildsByBranchHistory() {
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
        return o instanceof NoBuildsByBranchHistory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return NoBuildsByBranchHistory.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "NoBuildsByBranchHistory{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Do not store history of builds by branch";
        }
    }
}
