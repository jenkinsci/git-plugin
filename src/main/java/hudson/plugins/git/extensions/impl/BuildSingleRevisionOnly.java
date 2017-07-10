package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Don't trigger another build to catch up
 *
 * @author Sven Hickstein
 */
public class BuildSingleRevisionOnly extends GitSCMExtension {
    @DataBoundConstructor
    public BuildSingleRevisionOnly() {
    }

    @Override
    public boolean disableMultipleRevisionDetection() {
        return true;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Build single revision only";
        }
    }
}
