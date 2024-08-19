package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * When used with {@code org.jenkinsci.plugins.multiplescms.MultiSCM}, this differentiates a different instance.
 * Not strictly necessary any more since {@link GitSCM#getKey} will compute a default value, but can improve visual appearance of multiple-SCM changelogs.
 * @author Kohsuke Kawaguchi
 */
public class ScmName extends FakeGitSCMExtension {
    private final String name;

    @DataBoundConstructor
    public ScmName(String name) {
        this.name = name;
    }

    @Override
    @CheckForNull
    public String getDeprecationAlternative() {
        // This extension is not intended to be used in Pipeline
        // Custom SCM name should be set directly by Pipeline
        // arguments when performing multiple checkouts in a single
        // Pipeline.
        return "Use the custom scm name as the userRemoteConfigs value for 'name'";
    }

    public String getName() {
        return name;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Custom SCM name";
        }
    }
}
