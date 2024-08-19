package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Disable Workspace-less polling via "git ls-remote". Only needed for repository that don't support ls-remote.
 *
 * @author Kohsuke Kawaguchi
 */
public class DisableRemotePoll extends GitSCMExtension {

    @DataBoundConstructor
    public DisableRemotePoll() {
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return true;
    }

    @Override
    @CheckForNull
    public String getDeprecationAlternative() {
        // This extension is not intended to be used in Pipeline
        return "Use poll: false instead.";
    }

    @Extension
    // No @Symbol annotation, because force polling using workspace should not be used in Pipeline
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Force polling using workspace";
        }
    }
}
