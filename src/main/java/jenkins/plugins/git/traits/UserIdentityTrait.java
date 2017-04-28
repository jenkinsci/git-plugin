package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.UserIdentity;
import org.kohsuke.stapler.DataBoundConstructor;

public class UserIdentityTrait extends GitSCMExtensionTrait<UserIdentity> {
    @DataBoundConstructor
    public UserIdentityTrait(UserIdentity extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Custom user name/e-mail address";
        }
    }
}
