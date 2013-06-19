package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Workspace-less polling via "git ls-remote"
 *
 * @author Kohsuke Kawaguchi
 */
public class RemotePoll extends FakeGitSCMExtension {
    @DataBoundConstructor
    public RemotePoll() {
        /*
        if (remotePoll
            && (branches.size() != 1
            || branches.get(0).getName().contains("*")
            || userRemoteConfigs.size() != 1
// FIXME:   || (excludedRegions != null && excludedRegions.length() > 0)
            || (submoduleCfg.size() != 0)
// FIXME:   || (excludedUsers != null && excludedUsers.length() > 0)
        )) {
            LOGGER.log(Level.WARNING, "Cannot poll remotely with current configuration.");
            this.remotePoll = false;
        } else {
            this.remotePoll = remotePoll;
        }

         */
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Fast remote polling";
        }
    }
}
