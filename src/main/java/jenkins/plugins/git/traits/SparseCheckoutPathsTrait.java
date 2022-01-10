package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.SparseCheckoutPaths;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes {@link SparseCheckoutPaths} as a {@link SCMSourceTrait}.
 *
 * @since 4.0.1
 */
public class SparseCheckoutPathsTrait extends GitSCMExtensionTrait<SparseCheckoutPaths> {
    /**
     * Stapler constructor.
     *
     * @param extension the {@link SparseCheckoutPaths}
     */
    @DataBoundConstructor
    public SparseCheckoutPathsTrait(SparseCheckoutPaths extension) {
        super(extension);
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Sparse Checkout paths";
        }
    }
}
