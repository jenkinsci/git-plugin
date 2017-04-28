package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.CloneOption;
import org.kohsuke.stapler.DataBoundConstructor;

public class CloneOptionTrait extends GitSCMExtensionTrait<CloneOption> {
    @DataBoundConstructor
    public CloneOptionTrait(CloneOption extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Advanced clone behaviours";
        }
    }
}
