package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import org.kohsuke.stapler.DataBoundConstructor;

public class SubmoduleOptionTrait extends GitSCMExtensionTrait<SubmoduleOption> {
    @DataBoundConstructor
    public SubmoduleOptionTrait(SubmoduleOption extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Advanced sub-modules behaviours";
        }
    }
}
