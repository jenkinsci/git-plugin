package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.WipeWorkspace;
import org.kohsuke.stapler.DataBoundConstructor;

public class WipeWorkspaceTrait extends GitSCMExtensionTrait<WipeWorkspace> {
    @DataBoundConstructor
    public WipeWorkspaceTrait(WipeWorkspace extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Wipe out repository & force clone";
        }
    }
}
