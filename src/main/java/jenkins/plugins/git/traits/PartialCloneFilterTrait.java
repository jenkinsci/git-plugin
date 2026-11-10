package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.PartialCloneFilter;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes {@link PartialCloneFilter} as a {@link SCMSourceTrait}.
 *
 * @since 6.3.0
 */
public class PartialCloneFilterTrait extends GitSCMExtensionTrait<PartialCloneFilter> {

    /**
     * Stapler constructor.
     *
     * @param extension the {@link PartialCloneFilter}
     */
    @DataBoundConstructor
    public PartialCloneFilterTrait(PartialCloneFilter extension) {
        super(extension);
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    @Symbol("partialCloneFilter")
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Partial clone";
        }
    }
}
