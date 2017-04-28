package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.CleanBeforeCheckout;
import org.kohsuke.stapler.DataBoundConstructor;

public class CleanBeforeCheckoutTrait extends GitSCMExtensionTrait<CleanBeforeCheckout> {
    @DataBoundConstructor
    public CleanBeforeCheckoutTrait(CleanBeforeCheckout extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Clean before checkout";
        }
    }
}
