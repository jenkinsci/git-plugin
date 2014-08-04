package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Enforce JGit Client
 */
public class EnforceGitClient extends FakeGitSCMExtension {

    GitClientType clientType = GitClientType.ANY;

    public EnforceGitClient set(GitClientType type) {
        this.clientType = type;
        return this;
    }

    @Override
    public GitClientType getRequiredClient()
    {
        return clientType;
    }

    @DataBoundConstructor
    public EnforceGitClient() {
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Enforce JGit Client";
        }
    }
}
