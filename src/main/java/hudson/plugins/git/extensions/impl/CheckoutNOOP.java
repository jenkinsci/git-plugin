package hudson.plugins.git.extensions.impl;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

/**
 * Add option to suppress the checkout command.
 */
public class CheckoutNOOP extends FakeGitSCMExtension {

    @DataBoundConstructor
    public CheckoutNOOP() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
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

        return true; // all instances are equal.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // as this is like a singleton, use its classes hashCode to avoid collision with other Extensions in Collection
        return CheckoutNOOP.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "CheckoutNOOP";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Suppress checkout operation (NOOP)";
        }
    }

}
