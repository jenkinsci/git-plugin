package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.LimitBuildsByBranchHistory;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes {@link LimitBuildsByBranchHistory} as a {@link SCMSourceTrait}.
 * Multibranch jobs build a single branch, so their history already holds one entry;
 * the trait is there for sources that are configured with other branch specifiers.
 *
 * @since TODO
 */
public class LimitBuildsByBranchHistoryTrait extends GitSCMExtensionTrait<LimitBuildsByBranchHistory> {
    /**
     * Stapler constructor.
     */
    @DataBoundConstructor
    public LimitBuildsByBranchHistoryTrait() {
        super(new LimitBuildsByBranchHistory());
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    @Symbol("limitBuildsByBranchHistory")
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Keep build history only for branches matching the branch specifier";
        }
    }
}
