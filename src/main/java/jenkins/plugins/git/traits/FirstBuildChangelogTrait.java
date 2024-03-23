package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.FirstBuildChangelog;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * Exposes {@link FirstBuildChangelog} as a {@link SCMSourceTrait}.
 *
 * @since 5.2.0
 */
public class FirstBuildChangelogTrait extends GitSCMExtensionTrait<FirstBuildChangelog> {

    /**
     * @deprecated Use constructor that accepts extension instead.
     */
    @Deprecated
    public FirstBuildChangelogTrait() {
        this(null);
    }

    /**
     * Stapler constructor.
     *
     * @param extension the option to force first build to have a non-empty changelog.
     */
    @DataBoundConstructor
    public FirstBuildChangelogTrait(@CheckForNull FirstBuildChangelog extension) {
        super(extension == null ? new FirstBuildChangelog() : extension);
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    @Symbol("FirstBuildChangelog")
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "First Build Changelog";
        }
    }
}
