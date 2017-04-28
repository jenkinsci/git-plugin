package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CheckoutOption;
import hudson.plugins.git.extensions.impl.LocalBranch;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class LocalBranchTrait extends GitSCMExtensionTrait<LocalBranch> {
    @DataBoundConstructor
    public LocalBranchTrait() {
        super(new LocalBranch("**"));
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Check out to matching local branch";
        }

        @Override
        public SCMSourceTrait convertToTrait(GitSCMExtension extension) {
            LocalBranch ext = (LocalBranch) extension;
            if ("**".equals(StringUtils.defaultIfBlank(ext.getLocalBranch(), "**"))) {
                return new LocalBranchTrait();
            }
            // does not make sense to have any other type of LocalBranch in the context of SCMSource
            return null;
        }
    }
}
