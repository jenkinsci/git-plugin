package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import org.kohsuke.stapler.DataBoundConstructor;

public class PruneStaleBranchTrait extends GitSCMExtensionTrait<PruneStaleBranch> {
    @DataBoundConstructor
    public PruneStaleBranchTrait(PruneStaleBranch extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Prune stale remote-tracking branches";
        }
    }
}
