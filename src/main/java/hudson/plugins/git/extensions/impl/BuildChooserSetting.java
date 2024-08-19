package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildChooserDescriptor;
import hudson.plugins.git.util.DefaultBuildChooser;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

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

    @Override
    @CheckForNull
    public String getDeprecationAlternative() {
        // This extension is not intended to be used in Pipeline
        return "Use separate Pipeline jobs for individual branches instead of switching between branches with the same job.";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        public List<BuildChooserDescriptor> getBuildChooserDescriptors() {
            return BuildChooser.all();
        }

        public List<BuildChooserDescriptor> getBuildChooserDescriptors(Item job) {
            if (job == null) {
                return getBuildChooserDescriptors();
            }
            return BuildChooser.allApplicableTo(job);
        }

        @Override
        public String getDisplayName() {
            return "Strategy for choosing what to build";
        }
    }
}
