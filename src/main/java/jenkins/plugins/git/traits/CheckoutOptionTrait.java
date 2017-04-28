package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.CheckoutOption;
import org.kohsuke.stapler.DataBoundConstructor;

public class CheckoutOptionTrait extends GitSCMExtensionTrait<CheckoutOption> {
    @DataBoundConstructor
    public CheckoutOptionTrait(CheckoutOption extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Advanced checkout behaviours";
        }
    }
}
