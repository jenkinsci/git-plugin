package hudson.plugins.git;

import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

/**
 * Options for the {@link hudson.plugins.git.extensions.impl.ChangelogToBranch} extension.
 *
 * @author <a href="mailto:dirk.reske@t-systems.com">Dirk Reske (dirk.reske@t-systems.com)</a>
 */
public class ChangelogToBranchOptions extends AbstractDescribableImpl<ChangelogToBranchOptions> implements Serializable {
    private String compareRemote;
    private String compareTarget;

    @DataBoundConstructor
    public ChangelogToBranchOptions(String compareRemote, String compareTarget) {
        this.compareRemote = compareRemote;
        this.compareTarget = compareTarget;
    }

    public ChangelogToBranchOptions(ChangelogToBranchOptions options) {
        this(options.getCompareRemote(), options.getCompareTarget());
    }

    public String getCompareRemote() {
        return compareRemote;
    }

    public String getCompareTarget() {
        return compareTarget;
    }

    public String getRef() {
        return compareRemote + "/" + compareTarget;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ChangelogToBranchOptions> {

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
