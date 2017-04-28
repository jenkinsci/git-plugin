package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.GitLFSPull;
import org.kohsuke.stapler.DataBoundConstructor;

public class GitLFSPullTrait extends GitSCMExtensionTrait<GitLFSPull> {
    @DataBoundConstructor
    public GitLFSPullTrait(GitLFSPull extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Git LFS pull after checkout";
        }
    }
}
