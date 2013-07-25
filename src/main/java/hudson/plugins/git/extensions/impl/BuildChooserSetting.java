package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.DefaultBuildChooser;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Holds {@link BuildChooser}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildChooserSetting extends FakeGitSCMExtension {
    private BuildChooser buildChooser;

    @DataBoundConstructor
    public BuildChooserSetting(BuildChooser buildChooser) {
        this.buildChooser = buildChooser;
    }

    public BuildChooser getBuildChooser() {
        if (buildChooser==null)
            buildChooser = new DefaultBuildChooser();
        return buildChooser;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Strategy for choosing what to build";
        }
    }
}

