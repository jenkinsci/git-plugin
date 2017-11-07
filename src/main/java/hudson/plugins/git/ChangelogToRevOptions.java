package hudson.plugins.git;

import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

/**
 * Options for the {@link hudson.plugins.git.extensions.impl.ChangelogToRev} extension.
 *
 * @author <a href="mailto:jacob.e.keller@intel.com">Jacob Keller (jacob.e.keller@intel.com)</a>
 */
public class ChangelogToRevOptions extends AbstractDescribableImpl<ChangelogToRevOptions> implements ChangelogOptions, Serializable {
    private String revision;

    @DataBoundConstructor
    public ChangelogToRevOptions(String revision) {
        this.revision = revision;
    }

    public ChangelogToRevOptions(ChangelogOptions options) {
        this(options.getRevision());
    }

    public String getRevision() {
        return revision;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ChangelogToRevOptions> {

        @Override
        public String getDisplayName() {
            return "Options for Changelog to Revision strategy";
        }
    }
}
