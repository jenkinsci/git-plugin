package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import org.kohsuke.stapler.DataBoundConstructor;

public class CleanAfterCheckoutTrait extends GitSCMExtensionTrait<CleanCheckout> {
    @DataBoundConstructor
    public CleanAfterCheckoutTrait(CleanCheckout extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Clean after checkout";
        }
    }
}
