package hudson.plugins.git.extensions.impl;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.plugins.git.ChangelogOptions;
import hudson.plugins.git.ChangelogToRevOptions;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

/**
 * This extension activates the alternative changelog computation,
 * where the changelog is calculated against a specified branch.
 *
 * Similar to *{@link hudson.plugins.git.extensions.impl.ChangelogToBranch},
 * except allows arbitrary commit-ish as understood by git rev-parse such as
 * a ref or a commit id.
 *
 * To replicate the behavior of ChangelogToBranch, you can pass
 * "remote/branch" as the ref.
 *
 * @author <a href="mailto:dirk.reske@t-systems.com">Dirk Reske (dirk.reske@t-systems.com)</a>
 */
public class ChangelogToRev extends GitSCMExtension {

    private ChangelogToRevOptions options;

    @DataBoundConstructor
    public ChangelogToRev(ChangelogToRevOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("options may not be null");
        }
        this.options = options;
    }

    public ChangelogOptions getOptions() {
        return options;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Calculate changelog against a specific revision";
        }
    }
}
