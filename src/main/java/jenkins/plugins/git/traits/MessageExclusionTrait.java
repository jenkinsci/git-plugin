package jenkins.plugins.git.traits;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.MessageExclusion;

public class MessageExclusionTrait extends GitSCMExtensionTrait<MessageExclusion> {
    /**
     * Stapler constructor.
     *
     * @param extension the {@link MessageExclusion}.
     */
    @DataBoundConstructor
    public MessageExclusionTrait(MessageExclusion extension) {
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
            return "Message exclusion";
        }
    }
}
