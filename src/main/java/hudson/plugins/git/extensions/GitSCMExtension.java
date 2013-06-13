package hudson.plugins.git.extensions;

import hudson.model.AbstractDescribableImpl;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class GitSCMExtension extends AbstractDescribableImpl<GitSCMExtension> {
    @Override
    public GitSCMExtensionDescriptor getDescriptor() {
        return (GitSCMExtensionDescriptor) super.getDescriptor();
    }

    /**
     * Collects the actual {@link GitSCMExtension}s to be consulted.
     *
     * Allow a single user-configured {@link GitSCMExtension} to plug into multiple sub extension points.
     */
    public <T extends GitSCMExtension> void collectEffectiveExtensions(Class<T> type, List<T> extensions) {
        if (type.isInstance(this))
            extensions.add(type.cast(this));
    }
}
